#pragma once

#include <mbgl/map/map.hpp>
#include <mbgl/map/view.hpp>
#include <mbgl/map/backend.hpp>
#include <mbgl/util/noncopyable.hpp>
#include <mbgl/util/default_thread_pool.hpp>
#include <mbgl/util/run_loop.hpp>
#include <mbgl/storage/default_file_source.hpp>
#include <mbgl/storage/network_status.hpp>

#include <string>
#include <jni.h>

#include <jni/jni.hpp>

namespace mbgl {
namespace android {

class NativeMapView : public View, public Backend {
public:

    static constexpr auto Name() { return "com/mapbox/mapboxsdk/maps/NativeMapView"; };

    static jni::Class<NativeMapView> javaClass;

    static void registerNative(jni::JNIEnv&);

    NativeMapView(jni::JNIEnv&, jni::Object<NativeMapView>, jni::String, jni::String, jni::jfloat, jni::jint, jni::jlong);

    virtual ~NativeMapView();

    // mbgl::View //

    void bind() override;

    // mbgl::Backend //

    void invalidate() override;
    void notifyMapChange(mbgl::MapChange) override;

    // JNI //

    void destroy(jni::JNIEnv&);

    void render(jni::JNIEnv&);

    void onViewportChanged(jni::JNIEnv&, jni::jint width, jni::jint height);

    void setAPIBaseUrl(jni::JNIEnv&, jni::String);

    jni::String getStyleUrl(jni::JNIEnv&);

    void setStyleUrl(jni::JNIEnv&, jni::String);

    jni::String getStyleJson(jni::JNIEnv&);

    void setStyleJson(jni::JNIEnv&, jni::String);

    jni::String getAccessToken(jni::JNIEnv&);

    void setAccessToken(jni::JNIEnv&, jni::String);

    void cancelTransitions(jni::JNIEnv&);

    void setGestureInProgress(jni::JNIEnv&, jni::jboolean);

    void moveBy(jni::JNIEnv&, jni::jdouble, jni::jdouble);

    void setLatLng(jni::JNIEnv&, jni::jdouble, jni::jdouble);

    void setReachability(jni::JNIEnv&, jni::jboolean);

    void scheduleSnapshot(jni::JNIEnv&);

    void enableFps(jni::JNIEnv&, jni::jboolean enable);

protected:
    // Unused methods from mbgl::Backend //

    void activate() override {};
    void deactivate() override {};

private:
    void wake();
    void updateViewBinding();
    mbgl::Size getFramebufferSize() const;

    void resizeView(int width, int height);
    void resizeFramebuffer(int width, int height);

    void updateFps();

private:

    JavaVM *vm = nullptr;
    jni::UniqueWeakObject<NativeMapView> javaPeer;

    std::string styleUrl;
    std::string apiKey;

    float pixelRatio;
    bool fpsEnabled = false;
    bool snapshot = false;
    bool firstRender = true;
    double fps = 0.0;

    int width = 0;
    int height = 0;
    int fbWidth = 0;
    int fbHeight = 0;
    bool framebufferSizeChanged = true;

    int availableProcessors = 0;
    size_t totalMemory = 0;

    // Ensure these are initialised last
    std::unique_ptr<mbgl::util::RunLoop> runLoop;
    std::unique_ptr<mbgl::DefaultFileSource> fileSource;
    mbgl::ThreadPool threadPool;
    std::unique_ptr<mbgl::Map> map;
    mbgl::EdgeInsets insets;

};
}
}
