#include "native_map_view.hpp"
#include "jni.hpp"
#include <jni/jni.hpp>
#include "attach_env.hpp"

#include <cstdlib>
#include <ctime>
#include <cassert>
#include <memory>
#include <list>
#include <tuple>

#include <sys/system_properties.h>

#include <EGL/egl.h>

#include <mbgl/util/platform.hpp>
#include <mbgl/util/event.hpp>
#include <mbgl/util/logging.hpp>
#include <mbgl/gl/extension.hpp>
#include <mbgl/gl/context.hpp>
#include <mbgl/util/constants.hpp>
#include <mbgl/util/image.hpp>

#include "bitmap.hpp"
#include "run_loop_impl.hpp"

namespace mbgl {
namespace android {

NativeMapView::NativeMapView(jni::JNIEnv& _env, jni::Object<NativeMapView> _obj, jni::String _cachePath, jni::String _apkPath,
                             jni::jfloat _pixelRatio, jni::jint _availableProcessors, jni::jlong _totalMemory) :
    javaPeer(_obj.NewWeakGlobalRef(_env)),
    pixelRatio(_pixelRatio),
    availableProcessors(_availableProcessors),
    totalMemory(_totalMemory),
    runLoop(std::make_unique<mbgl::util::RunLoop>(mbgl::util::RunLoop::Type::New)),
    threadPool(4) {

    mbgl::Log::Info(mbgl::Event::Android, "NativeMapView::NativeMapView");

    //Add a wake function to wake the render thread when needed
    mbgl::util::RunLoop::Impl* loop = reinterpret_cast<mbgl::util::RunLoop::Impl*>(mbgl::util::RunLoop::getLoopHandle());
    loop->setWakeFunction(std::bind(&NativeMapView::wake, this));

    // Get a reference to the JavaVM for callbacks
    //TODO: Why?
    if (_env.GetJavaVM(&vm) < 0) {
        _env.ExceptionDescribe();
        return;
    }

    // Create a default file source for this map instance
    fileSource = std::make_unique<mbgl::DefaultFileSource>(
        jni::Make<std::string>(_env, _cachePath) + "/mbgl-offline.db",
        jni::Make<std::string>(_env, _apkPath));

    // Create the core map
    map = std::make_unique<mbgl::Map>(
        *this, mbgl::Size{ static_cast<uint32_t>(width), static_cast<uint32_t>(height) },
        pixelRatio, *fileSource, threadPool, MapMode::Continuous);

    //Calculate a fitting cache size based on device parameters
    float zoomFactor   = map->getMaxZoom() - map->getMinZoom() + 1;
    float cpuFactor    = availableProcessors;
    float memoryFactor = static_cast<float>(totalMemory) / 1000.0f / 1000.0f / 1000.0f;
    float sizeFactor   = (static_cast<float>(map->getSize().width)  / mbgl::util::tileSize) *
        (static_cast<float>(map->getSize().height) / mbgl::util::tileSize);

    map->setSourceTileCacheSize(zoomFactor * cpuFactor * memoryFactor * sizeFactor * 0.5f);
}

/**
 * Called from Finalizer thread, not the rendering thread.
 * Don't mess with the state here
 */
NativeMapView::~NativeMapView() {
    mbgl::Log::Info(mbgl::Event::Android, "NativeMapView::~NativeMapView");
}

/**
 * From mbgl::View
 */
void NativeMapView::bind() {
    getContext().bindFramebuffer = 0;
    getContext().viewport = { 0, 0, getFramebufferSize() };
}

/**
 * From mbgl::Backend. Callback to java NativeMapView#onInvalidate().
 *
 * May be called from any thread
 */
void NativeMapView::invalidate() {
    Log::Info(mbgl::Event::Android, "NativeMapView::invalidate");
    android::UniqueEnv _env = android::AttachEnv();
    static auto onInvalidate = javaClass.GetMethod<void ()>(*_env, "onInvalidate");
    javaPeer->Call(*_env, onInvalidate);
}

/**
 * From mbgl::Backend. Callback to java NativeMapView#onMapChanged(int).
 *
 * May be called from any thread
 */
void NativeMapView::notifyMapChange(mbgl::MapChange change) {
    mbgl::Log::Info(mbgl::Event::Android, "notifyMapChange");
    assert(vm != nullptr);

    android::UniqueEnv _env = android::AttachEnv();
    static auto onMapChanged = javaClass.GetMethod<void (int)>(*_env, "onMapChanged");
    javaPeer->Call(*_env, onMapChanged, (int) change);
}

// JNI Methods //

/**
 * Custom destroy function for cleanup that needs to be run on the
 * render thread.
 */
void NativeMapView::destroy(jni::JNIEnv&) {
    mbgl::Log::Info(mbgl::Event::Android, "NativeMapView::destroy");

    //Remove the wake function as the JVM object is going to be GC'd pretty soon
    mbgl::util::RunLoop::Impl* loop = reinterpret_cast<mbgl::util::RunLoop::Impl*>(mbgl::util::RunLoop::getLoopHandle());
    loop->setWakeFunction(nullptr);

    map.reset();
    fileSource.reset();

    vm = nullptr;
}

void NativeMapView::onViewportChanged(jni::JNIEnv&, jni::jint w, jni::jint h) {
    resizeView((int) w / pixelRatio, (int) h / pixelRatio);
    resizeFramebuffer(w, h);
}

void NativeMapView::render(jni::JNIEnv& env) {
    mbgl::Log::Info(mbgl::Event::Android, "NativeMapView::render");

    if (firstRender) {
        mbgl::Log::Info(mbgl::Event::Android, "Initialize GL extensions");
        mbgl::gl::InitializeExtensions([] (const char * name) {
             return reinterpret_cast<mbgl::gl::glProc>(eglGetProcAddress(name));
        });
        firstRender = false;
    }

    //First, spin the run loop to process the queue (as there is no actual loop on the render thread)
    mbgl::util::RunLoop::Get()->runOnce();

    if (framebufferSizeChanged) {
        getContext().viewport = { 0, 0, getFramebufferSize() };
        framebufferSizeChanged = false;
    }

    updateViewBinding();
    map->render(*this);

    if (snapshot){
        snapshot = false;

        // take snapshot
        auto image = getContext().readFramebuffer<mbgl::PremultipliedImage>(getFramebufferSize());
        auto bitmap = Bitmap::CreateBitmap(env, std::move(image));

        android::UniqueEnv _env = android::AttachEnv();
        static auto onSnapshotReady = javaClass.GetMethod<void (jni::Object<Bitmap>)>(*_env, "onSnapshotReady");
        javaPeer->Call(*_env, onSnapshotReady, bitmap);
    }

    updateFps();
}

void NativeMapView::setAPIBaseUrl(jni::JNIEnv& env, jni::String url) {
    fileSource->setAPIBaseURL(jni::Make<std::string>(env, url));
}

jni::String NativeMapView::getStyleUrl(jni::JNIEnv& env) {
    return jni::Make<jni::String>(env, map->getStyleURL());
}

void NativeMapView::setStyleUrl(jni::JNIEnv& env, jni::String url) {
    map->setStyleURL(jni::Make<std::string>(env, url));
}

jni::String NativeMapView::getStyleJson(jni::JNIEnv& env) {
    return jni::Make<jni::String>(env, map->getStyleJSON());
}

void NativeMapView::setStyleJson(jni::JNIEnv& env, jni::String json) {
    map->setStyleJSON(jni::Make<std::string>(env, json));
}

jni::String NativeMapView::getAccessToken(jni::JNIEnv& env) {
    return jni::Make<jni::String>(env, fileSource->getAccessToken());
}

void NativeMapView::setAccessToken(jni::JNIEnv& env, jni::String token) {
    fileSource->setAccessToken(jni::Make<std::string>(env, token));
}

void NativeMapView::cancelTransitions(jni::JNIEnv&) {
    map->cancelTransitions();
}

void NativeMapView::setGestureInProgress(jni::JNIEnv&, jni::jboolean inProgress) {
    map->setGestureInProgress(inProgress);
}

void NativeMapView::moveBy(jni::JNIEnv&, jni::jdouble dx, jni::jdouble dy) {
    map->moveBy({dx, dy});
}

void NativeMapView::setLatLng(jni::JNIEnv&, jni::jdouble latitude, jni::jdouble longitude) {
    map->setLatLng(mbgl::LatLng(latitude, longitude), insets);
}

void NativeMapView::setReachability(jni::JNIEnv&, jni::jboolean reachable) {
    if (reachable) {
        mbgl::NetworkStatus::Reachable();
    }
}

void NativeMapView::scheduleSnapshot(jni::JNIEnv&) {
    snapshot = true;
}

void NativeMapView::enableFps(jni::JNIEnv&, jni::jboolean enable) {
    fpsEnabled = enable;
}

// Private methods //

mbgl::Size NativeMapView::getFramebufferSize() const {
    mbgl::Log::Info(mbgl::Event::Android, "FB size %dx%d", fbWidth, fbHeight);
    return { static_cast<uint32_t>(fbWidth), static_cast<uint32_t>(fbHeight) };
}

/**
 * Called whenever the associated thread needs to wake up (since there is no active run loop)
 *
 * May be called from any thread
 */
void NativeMapView::wake() {
    mbgl::Log::Info(mbgl::Event::JNI, "Wake callback");
    android::UniqueEnv _env = android::AttachEnv();
    static auto wakeCallback = javaClass.GetMethod<void ()>(*_env, "onWake");
    javaPeer->Call(*_env, wakeCallback);
}

void NativeMapView::updateViewBinding() {
    getContext().bindFramebuffer.setCurrentValue(0);
    assert(mbgl::gl::value::BindFramebuffer::Get() == getContext().bindFramebuffer.getCurrentValue());
    getContext().viewport.setCurrentValue({ 0, 0, getFramebufferSize() });
    assert(mbgl::gl::value::Viewport::Get() == getContext().viewport.getCurrentValue());
}

void NativeMapView::resizeView(int w, int h) {
    mbgl::Log::Info(mbgl::Event::Android, "resizeView %ix%i", w, h);
    width = w;
    height = h;
    map->setSize({ static_cast<uint32_t>(width), static_cast<uint32_t>(height) });
}

void NativeMapView::resizeFramebuffer(int w, int h) {
    mbgl::Log::Info(mbgl::Event::Android, "resizeFramebuffer %ix%i", w, h);
    fbWidth = w;
    fbHeight = h;
    framebufferSizeChanged = true;
    invalidate();
}

void NativeMapView::updateFps() {
    if (!fpsEnabled) {
        return;
    }

    static int frames = 0;
    static int64_t timeElapsed = 0LL;

    frames++;
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    int64_t currentTime = now.tv_sec * 1000000000LL + now.tv_nsec;

    if (currentTime - timeElapsed >= 1) {
        fps = frames / ((currentTime - timeElapsed) / 1E9);
        mbgl::Log::Info(mbgl::Event::Render, "FPS: %4.2f", fps);
        timeElapsed = currentTime;
        frames = 0;
    }

    assert(vm != nullptr);

    android::UniqueEnv _env = android::AttachEnv();
    static auto onFpsChanged = javaClass.GetMethod<void (double)>(*_env, "onFpsChanged");
    javaPeer->Call(*_env, onFpsChanged, fps);
}

// Static methods //

jni::Class<NativeMapView> NativeMapView::javaClass;

void NativeMapView::registerNative(jni::JNIEnv& env) {
    // Lookup the class
    NativeMapView::javaClass = *jni::Class<NativeMapView>::Find(env).NewGlobalRef(env).release();

    #define METHOD(MethodPtr, name) jni::MakeNativePeerMethod<decltype(MethodPtr), (MethodPtr)>(name)

    // Register the peer
    jni::RegisterNativePeer<NativeMapView>(env, NativeMapView::javaClass, "nativePtr",
            std::make_unique<NativeMapView, JNIEnv&, jni::Object<NativeMapView>, jni::String, jni::String, jni::jfloat, jni::jint, jni::jlong>,
            "initialize",
            "finalize",
            METHOD(&NativeMapView::destroy, "destroy"),
            METHOD(&NativeMapView::render, "render"),
            METHOD(&NativeMapView::onViewportChanged, "nativeOnViewportChanged"),
            METHOD(&NativeMapView::setAPIBaseUrl  , "nativeSetAPIBaseUrl"),
            METHOD(&NativeMapView::getStyleUrl, "nativeGetStyleUrl"),
            METHOD(&NativeMapView::setStyleUrl, "nativeSetStyleUrl"),
            METHOD(&NativeMapView::getStyleJson, "nativeGetStyleJson"),
            METHOD(&NativeMapView::setStyleJson, "nativeSetStyleJson"),
            METHOD(&NativeMapView::getAccessToken, "nativeGetAccessToken"),
            METHOD(&NativeMapView::setAccessToken, "nativeSetAccessToken"),
            METHOD(&NativeMapView::cancelTransitions, "nativeCancelTransitions"),
            METHOD(&NativeMapView::setGestureInProgress, "nativeSetGestureInProgress"),
            METHOD(&NativeMapView::moveBy, "nativeMoveBy"),
            METHOD(&NativeMapView::setLatLng, "nativeSetLatLng"),
            METHOD(&NativeMapView::setReachability, "nativeSetReachability"),
            METHOD(&NativeMapView::scheduleSnapshot, "nativeScheduleSnapshot"),
            METHOD(&NativeMapView::enableFps, "nativeEnableFps")
    );
}

}
}
