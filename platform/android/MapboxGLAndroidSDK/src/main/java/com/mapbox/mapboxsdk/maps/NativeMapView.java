package com.mapbox.mapboxsdk.maps;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.ProjectedMeters;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.NoSuchLayerException;
import com.mapbox.mapboxsdk.style.sources.NoSuchSourceException;
import com.mapbox.mapboxsdk.style.sources.Source;
import com.mapbox.services.commons.geojson.Feature;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

// Class that wraps the native methods for convenience
final class NativeMapView {

  // Holds the pointer to JNI NativeMapView
  private long nativePtr = 0;

  // Used for callbacks
  private MapView mapView;

  // Device density
  private final float pixelRatio;

  // Listeners for Map change events
  private CopyOnWriteArrayList<MapView.OnMapChangedListener> onMapChangedListeners = new CopyOnWriteArrayList<>();

  // Listener invoked to return a bitmap of the map
  private MapboxMap.SnapshotReadyCallback snapshotReadyCallback;

  //
  // Static methods
  //

  static {
    System.loadLibrary("mapbox-gl");
  }

  //
  // Constructors
  //

  public NativeMapView(MapView mapView) {
    this.mapView = mapView;

    Context context = mapView.getContext();
    String cachePath = OfflineManager.getDatabasePath(context);

    pixelRatio = context.getResources().getDisplayMetrics().density;
    String apkPath = context.getPackageCodePath();

    int availableProcessors = Runtime.getRuntime().availableProcessors();

    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    activityManager.getMemoryInfo(memoryInfo);

    long totalMemory = memoryInfo.availMem;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      totalMemory = memoryInfo.totalMem;
    }

    if (availableProcessors < 0) {
      throw new IllegalArgumentException("availableProcessors cannot be negative.");
    }

    if (totalMemory < 0) {
      throw new IllegalArgumentException("totalMemory cannot be negative.");
    }

    initialize(this, cachePath, apkPath, pixelRatio, availableProcessors, totalMemory);
  }

  //
  // Methods
  //

  void onViewportChanged(int width, int height) {
    if (width < 0) {
      throw new IllegalArgumentException("width cannot be negative.");
    }

    if (height < 0) {
      throw new IllegalArgumentException("height cannot be negative.");
    }

    if (width > 65535) {
      // we have seen edge cases where devices return incorrect values #6111
      Timber.e("Device returned an out of range width size, "
        + "capping value at 65535 instead of " + width);
      width = 65535;
    }

    if (height > 65535) {
      // we have seen edge cases where devices return incorrect values #6111
      Timber.e("Device returned an out of range height size, "
        + "capping value at 65535 instead of " + height);
      height = 65535;
    }

    nativeOnViewportChanged(width, height);
  }

  void update() {
    Timber.w("TODO; Implement update()");
    onInvalidate();
  }

  public void setStyleUrl(String url) {
    nativeSetStyleUrl(url);
  }

  public String getStyleUrl() {
    return nativeGetStyleUrl();
  }

  public void setStyleJson(String newStyleJson) {
    nativeSetStyleJson(newStyleJson);
  }

  public String getStyleJson() {
    return nativeGetStyleJson();
  }

  public void setAccessToken(String accessToken) {
    Timber.i("Access token: %s", accessToken);
    nativeSetAccessToken(accessToken);
  }

  public String getAccessToken() {
    return nativeGetAccessToken();
  }

  public void cancelTransitions() {
    nativeCancelTransitions();
  }

  public void setGestureInProgress(boolean inProgress) {
    nativeSetGestureInProgress(inProgress);
  }

  public void moveBy(double dx, double dy) {
    Timber.i("Move by %sx%s", dx, dy);
    nativeMoveBy(dx / pixelRatio, dy / pixelRatio);
  }

  @Deprecated
  public void moveBy(double dx, double dy, long duration) {
    moveBy(dx, dy);
  }

  public void setLatLng(LatLng latLng) {
    Timber.i("setLatLng %sx%s - %s", latLng.getLatitude(), latLng.getLongitude());
    nativeSetLatLng(latLng.getLatitude(), latLng.getLongitude());
  }

  @Deprecated
  public void setLatLng(LatLng latLng, long duration) {
    setLatLng(latLng);
  }

  public LatLng getLatLng() {
    // wrap longitude values coming from core
    //TODO return nativeGetLatLng(nativeMapViewPtr).wrap();
    return null;
  }

  public void resetPosition() {
    //TODO nativeResetPosition(nativeMapViewPtr);
  }

  public double getPitch() {
    //TODO return nativeGetPitch(nativeMapViewPtr);
    return 0;
  }

  public void setPitch(double pitch, long duration) {
    Timber.i("setLatLng %s - %s", pitch, duration);
    //TODO  nativeSetPitch(nativeMapViewPtr, pitch, duration);
  }

  public void scaleBy(double ds) {
    //TODO scaleBy(ds, Double.NaN, Double.NaN);
  }

  public void scaleBy(double ds, double cx, double cy) {
    scaleBy(ds, cx, cy, 0);
  }

  @Deprecated
  public void scaleBy(double ds, double cx, double cy, long duration) {
    //TODO nativeScaleBy(nativeMapViewPtr, ds, cx / pixelRatio, cy / pixelRatio, duration);
  }

  public void setScale(double scale) {
    setScale(scale, Double.NaN, Double.NaN);
  }

  public void setScale(double scale, double cx, double cy) {
    setScale(scale, cx, cy, 0);
  }

  @Deprecated
  public void setScale(double scale, double cx, double cy, long duration) {
    //TODO nativeSetScale(nativeMapViewPtr, scale, cx / pixelRatio, cy / pixelRatio, duration);
  }

  public double getScale() {
    //TODO return nativeGetScale(nativeMapViewPtr);
    return 0;
  }

  public void setZoom(double zoom) {
    setZoom(zoom, 0);
  }

  @Deprecated
  public void setZoom(double zoom, long duration) {
    //TODO nativeSetZoom(nativeMapViewPtr, zoom, duration);
  }

  public double getZoom() {
    //TODO return nativeGetZoom(nativeMapViewPtr);
    return 0;
  }

  public void resetZoom() {
    //TODO nativeResetZoom(nativeMapViewPtr);
  }

  public void setMinZoom(double zoom) {
    //TODO nativeSetMinZoom(nativeMapViewPtr, zoom);
  }

  public double getMinZoom() {
    //TODO return nativeGetMinZoom(nativeMapViewPtr);
    return 0;
  }

  public void setMaxZoom(double zoom) {
    //TODO nativeSetMaxZoom(nativeMapViewPtr, zoom);
  }

  public double getMaxZoom() {
    //TODO return nativeGetMaxZoom(nativeMapViewPtr);
    return 0;
  }

  public void rotateBy(double sx, double sy, double ex, double ey) {
    rotateBy(sx, sy, ex, ey, 0);
  }

  public void rotateBy(double sx, double sy, double ex, double ey,
                       long duration) {
    //TODO   nativeRotateBy(nativeMapViewPtr, sx / pixelRatio, sy / pixelRatio, ex, ey, duration);
  }

  public void setContentPadding(int[] padding) {
    //TODO
//    nativeSetContentPadding(nativeMapViewPtr,
//      padding[1] / pixelRatio,
//      padding[0] / pixelRatio,
//      padding[3] / pixelRatio,
//      padding[2] / pixelRatio);
  }

  public void setBearing(double degrees) {
    setBearing(degrees, 0);
  }

  public void setBearing(double degrees, long duration) {
    //TODO nativeSetBearing(nativeMapViewPtr, degrees, duration);
  }

  public void setBearing(double degrees, double cx, double cy) {
    //TODO nativeSetBearingXY(nativeMapViewPtr, degrees, cx / pixelRatio, cy / pixelRatio);
  }

  public double getBearing() {
    //TODO return nativeGetBearing(nativeMapViewPtr);
    return 0;
  }

  public void resetNorth() {
    //TODO nativeResetNorth(nativeMapViewPtr);
  }

  public long addMarker(Marker marker) {
    Marker[] markers = {marker};
    //TODO return nativeAddMarkers(nativeMapViewPtr, markers)[0];
    return 0;
  }

  public long[] addMarkers(List<Marker> markers) {
    //TODO return nativeAddMarkers(nativeMapViewPtr, markers.toArray(new Marker[markers.size()]));
    return null;
  }

  public long addPolyline(Polyline polyline) {
    Polyline[] polylines = {polyline};
    //TODO return nativeAddPolylines(nativeMapViewPtr, polylines)[0];
    return 0;
  }

  public long[] addPolylines(List<Polyline> polylines) {
    //TODO return nativeAddPolylines(nativeMapViewPtr, polylines.toArray(new Polyline[polylines.size()]));
    return null;
  }

  public long addPolygon(Polygon polygon) {
    Polygon[] polygons = {polygon};
    //TODO return nativeAddPolygons(nativeMapViewPtr, polygons)[0];
    return 0;
  }

  public long[] addPolygons(List<Polygon> polygons) {
    //TODO return nativeAddPolygons(nativeMapViewPtr, polygons.toArray(new Polygon[polygons.size()]));
    return null;
  }

  public void updateMarker(Marker marker) {
    LatLng position = marker.getPosition();
    Icon icon = marker.getIcon();
    //TODO nativeUpdateMarker(nativeMapViewPtr, marker.getId(), position.getLatitude(), position.getLongitude(), icon.getId());
  }

  public void updatePolygon(Polygon polygon) {
    //TODO nativeUpdatePolygon(nativeMapViewPtr, polygon.getId(), polygon);
  }

  public void updatePolyline(Polyline polyline) {
    //TODO nativeUpdatePolyline(nativeMapViewPtr, polyline.getId(), polyline);
  }

  public void removeAnnotation(long id) {
    long[] ids = {id};
    removeAnnotations(ids);
  }

  public void removeAnnotations(long[] ids) {
    //TODO nativeRemoveAnnotations(nativeMapViewPtr, ids);
  }

  public long[] queryPointAnnotations(RectF rect) {
    //TODO return nativeQueryPointAnnotations(nativeMapViewPtr, rect);
    return new long[0];
  }

  public void addAnnotationIcon(String symbol, int width, int height, float scale, byte[] pixels) {
    //TODO nativeAddAnnotationIcon(nativeMapViewPtr, symbol, width, height, scale, pixels);
  }

  public void setVisibleCoordinateBounds(LatLng[] coordinates, RectF padding, double direction, long duration) {
    //TODO nativeSetVisibleCoordinateBounds(nativeMapViewPtr, coordinates, padding, direction, duration);
  }

  public void onLowMemory() {
    //TODO nativeOnLowMemory(nativeMapViewPtr);
  }

  public void setDebug(boolean debug) {
    //TODO nativeSetDebug(nativeMapViewPtr, debug);
  }

  public void cycleDebugOptions() {
    //TODO nativeToggleDebug(nativeMapViewPtr);
  }

  public boolean getDebug() {
    //TODO return nativeGetDebug(nativeMapViewPtr);
    return false;
  }

  public boolean isFullyLoaded() {
    //TODO return nativeIsFullyLoaded(nativeMapViewPtr);
    return false;
  }

  public void setReachability(boolean status) {
    nativeSetReachability(status);
  }

  public double getMetersPerPixelAtLatitude(double lat) {
    //TODO return nativeGetMetersPerPixelAtLatitude(nativeMapViewPtr, lat, getZoom());
    return 0;
  }

  public ProjectedMeters projectedMetersForLatLng(LatLng latLng) {
    //TODO return nativeProjectedMetersForLatLng(nativeMapViewPtr, latLng.getLatitude(), latLng.getLongitude());
    return null;
  }

  public LatLng latLngForProjectedMeters(ProjectedMeters projectedMeters) {
    //TODO return nativeLatLngForProjectedMeters(nativeMapViewPtr, projectedMeters.getNorthing(),
    // projectedMeters.getEasting()).wrap();
    return null;
  }

  public PointF pixelForLatLng(LatLng latLng) {
//    PointF pointF = nativePixelForLatLng(nativeMapViewPtr, latLng.getLatitude(), latLng.getLongitude());
//    pointF.set(pointF.x * pixelRatio, pointF.y * pixelRatio);
//    return pointF;
    //TODO
    return null;
  }

  public LatLng latLngForPixel(PointF pixel) {
    //TODO return nativeLatLngForPixel(nativeMapViewPtr, pixel.x / pixelRatio, pixel.y / pixelRatio).wrap();
    return new LatLng();
  }

  public double getTopOffsetPixelsForAnnotationSymbol(String symbolName) {
    //tODO return nativeGetTopOffsetPixelsForAnnotationSymbol(nativeMapViewPtr, symbolName);
    return 0;
  }

  @Deprecated
  public void jumpTo(double angle, LatLng center, double pitch, double zoom) {
    Timber.w("Deprecated");
  }

  @Deprecated
  public void easeTo(double angle, LatLng center, long duration, double pitch, double zoom,
                     boolean easingInterpolator) {
    Timber.w("Deprecated");
  }

  @Deprecated
  public void flyTo(double angle, LatLng center, long duration, double pitch, double zoom) {
    Timber.w("Deprecated");
  }

  public double[] getCameraValues() {
    //return nativeGetCameraValues(nativeMapViewPtr);
    return new double[0];
  }

  // Runtime style Api

  public long getTransitionDuration() {
    //TODO return nativeGetTransitionDuration(nativeMapViewPtr);
    return 0l;
  }

  public void setTransitionDuration(long duration) {
    //TODO nativeSetTransitionDuration(nativeMapViewPtr, duration);
  }

  public long getTransitionDelay() {
    return 0L;
    //TODO return nativeGetTransitionDelay(nativeMapViewPtr);
  }

  public void setTransitionDelay(long delay) {
    //TODO nativeSetTransitionDelay(nativeMapViewPtr, delay);
  }

  public Layer getLayer(String layerId) {
    //TODO return nativeGetLayer(nativeMapViewPtr, layerId);
    return null;
  }

  public void addLayer(@NonNull Layer layer, @Nullable String before) {
    //TODO nativeAddLayer(nativeMapViewPtr, layer.getNativePtr(), before);
  }

  public void removeLayer(@NonNull String layerId) throws NoSuchLayerException {
    //TODO nativeRemoveLayerById(nativeMapViewPtr, layerId);
  }

  public void removeLayer(@NonNull Layer layer) throws NoSuchLayerException {
    //TODO nativeRemoveLayer(nativeMapViewPtr, layer.getNativePtr());
  }

  public Source getSource(@NonNull String sourceId) {
    //TODO return nativeGetSource(nativeMapViewPtr, sourceId);
    return null;
  }

  public void addSource(@NonNull Source source) {
    //TODO nativeAddSource(nativeMapViewPtr, source.getNativePtr());
  }

  public void removeSource(@NonNull String sourceId) throws NoSuchSourceException {
    //TODO nativeRemoveSourceById(nativeMapViewPtr, sourceId);
  }

  public void removeSource(@NonNull Source source) throws NoSuchSourceException {
    //TODO nativeRemoveSource(nativeMapViewPtr, source.getNativePtr());
  }

  public void addImage(@NonNull String name, @NonNull Bitmap image) {
    // Check/correct config
    if (image.getConfig() != Bitmap.Config.ARGB_8888) {
      image = image.copy(Bitmap.Config.ARGB_8888, false);
    }

    // Get pixels
    ByteBuffer buffer = ByteBuffer.allocate(image.getByteCount());
    image.copyPixelsToBuffer(buffer);

    // Determine pixel ratio
    float density = image.getDensity() == Bitmap.DENSITY_NONE ? Bitmap.DENSITY_NONE : image.getDensity();
    float pixelRatio = density / DisplayMetrics.DENSITY_DEFAULT;

    //TODO nativeAddImage(nativeMapViewPtr, name, image.getWidth(), image.getHeight(), pixelRatio, buffer.array());
  }

  public void removeImage(String name) {
    //TODO nativeRemoveImage(nativeMapViewPtr, name);
  }

  // Feature querying

  @NonNull
  public List<Feature> queryRenderedFeatures(PointF coordinates, String... layerIds) {
//    Feature[] features = nativeQueryRenderedFeaturesForPoint(nativeMapViewPtr, coordinates.x / pixelRatio,
//      coordinates.y / pixelRatio, layerIds);
//    return features != null ? Arrays.asList(features) : new ArrayList<Feature>();
    //TODO
    return new ArrayList<Feature>();
  }

  @NonNull
  public List<Feature> queryRenderedFeatures(RectF coordinates, String... layerIds) {
//    Feature[] features = nativeQueryRenderedFeaturesForBox(
//      nativeMapViewPtr,
//      coordinates.left / pixelRatio,
//      coordinates.top / pixelRatio,
//      coordinates.right / pixelRatio,
//      coordinates.bottom / pixelRatio,
//      layerIds);
//    return features != null ? Arrays.asList(features) : new ArrayList<Feature>();
//TODO
    return new ArrayList<Feature>();
  }

  public void scheduleTakeSnapshot() {
    nativeScheduleSnapshot();
  }

  public void setApiBaseUrl(String baseUrl) {
    nativeSetAPIBaseUrl(baseUrl);
  }

  public float getPixelRatio() {
    return pixelRatio;
  }

  public Context getContext() {
    return mapView.getContext();
  }

  //
  // Callbacks
  //

  /**
   * Called through JNI when the map needs to be re-rendered
   */
  protected void onInvalidate() {
    Timber.i("onInvalidate");
    mapView.onInvalidate();
  }

  /**
   * Called through JNI when the render thread needs to be woken up
   */
  protected void onWake() {
    Timber.i("wake!");
    mapView.requestRender();
  }

  /**
   * Called through JNI when the map state changed
   *
   * @param rawChange the mbgl::MapChange as an int
   */
  protected void onMapChanged(final int rawChange) {
    Timber.i("onMapChanged: %s", rawChange);
    if (onMapChangedListeners != null) {
      for (final MapView.OnMapChangedListener onMapChangedListener : onMapChangedListeners) {
        mapView.post(new Runnable() {
          @Override
          public void run() {
            onMapChangedListener.onMapChanged(rawChange);
          }
        });
      }
    }
  }

  /**
   * Called through JNI if fps is enabled and the fps changed
   *
   * @param fps the Frames Per Second
   */
  protected void onFpsChanged(double fps) {
    mapView.onFpsChanged(fps);
  }

  /**
   * Called through JNI when a requested snapshot is ready
   *
   * @param bitmap the snapshot as a bitmap
   */
  protected void onSnapshotReady(Bitmap bitmap) {
    if (snapshotReadyCallback != null && bitmap != null) {
      snapshotReadyCallback.onSnapshotReady(bitmap);
    }
  }

  //
  // JNI methods
  //

  private native void initialize(NativeMapView nativeMapView, String cachePath, String apkPath, float pixelRatio,
                                 int availableProcessors, long totalMemory);

  @Override
  protected native void finalize() throws Throwable;

  protected native void destroy();

  protected native void render();

  private native void nativeOnViewportChanged(int width, int height);

  private native void nativeSetStyleUrl(String url);

  private native String nativeGetStyleUrl();

  private native void nativeSetStyleJson(String styleJson);

  private native String nativeGetStyleJson();

  private native void nativeSetAccessToken(String accessToken);

  private native String nativeGetAccessToken();

  private native void nativeCancelTransitions();

  private native void nativeSetGestureInProgress(boolean inProgress);

  private native void nativeMoveBy(double dx, double dy);

  private native void nativeSetLatLng(double latitude, double longitude);

  //private native LatLng nativeGetLatLng();

  //
//  private native void nativeResetPosition(long nativeMapViewPtr);
//
//  private native double nativeGetPitch(long nativeMapViewPtr);
//
//  private native void nativeSetPitch(long nativeMapViewPtr, double pitch);
//
//  private native void nativeScaleBy(long nativeMapViewPtr, double ds, double cx, double cy);
//
//  private native void nativeSetScale(long nativeMapViewPtr, double scale, double cx, double cy);
//
//  private native double nativeGetScale(long nativeMapViewPtr);
//
//  private native void nativeSetZoom(long nativeMapViewPtr, double zoom);
//
//  private native double nativeGetZoom(long nativeMapViewPtr);
//
//  private native void nativeResetZoom(long nativeMapViewPtr);
//
//  private native void nativeSetMinZoom(long nativeMapViewPtr, double zoom);
//
//  private native double nativeGetMinZoom(long nativeMapViewPtr);
//
//  private native void nativeSetMaxZoom(long nativeMapViewPtr, double zoom);
//
//  private native double nativeGetMaxZoom(long nativeMapViewPtr);
//
//  private native void nativeRotateBy(long nativeMapViewPtr, double sx, double sy, double ex, double ey);
//
//  private native void nativeSetContentPadding(long nativeMapViewPtr, double top, double left, double bottom, double right);
//
//  private native void nativeSetBearing(long nativeMapViewPtr, double degrees);
//
//  private native void nativeSetBearingXY(long nativeMapViewPtr, double degrees, double cx, double cy);
//
//  private native double nativeGetBearing(long nativeMapViewPtr);
//
//  private native void nativeResetNorth(long nativeMapViewPtr);
//
//  private native void nativeUpdateMarker(long nativeMapViewPtr, long markerId, double lat, double lon, String iconId);
//
//  private native long[] nativeAddMarkers(long nativeMapViewPtr, Marker[] markers);
//
//  private native long[] nativeAddPolylines(long nativeMapViewPtr, Polyline[] polylines);
//
//  private native long[] nativeAddPolygons(long nativeMapViewPtr, Polygon[] polygons);
//
//  private native void nativeRemoveAnnotations(long nativeMapViewPtr, long[] id);
//
//  private native long[] nativeQueryPointAnnotations(long nativeMapViewPtr, RectF rect);
//
//  private native void nativeAddAnnotationIcon(long nativeMapViewPtr, String symbol,
//                                              int width, int height, float scale, byte[] pixels);
//
//  private native void nativeSetVisibleCoordinateBounds(long nativeMapViewPtr, LatLng[] coordinates, RectF padding, double direction);
//
//  private native void nativeOnLowMemory(long nativeMapViewPtr);
//
//  private native void nativeSetDebug(long nativeMapViewPtr, boolean debug);
//
//  private native void nativeToggleDebug(long nativeMapViewPtr);
//
//  private native boolean nativeGetDebug(long nativeMapViewPtr);
//
//  private native boolean nativeIsFullyLoaded(long nativeMapViewPtr);

  private native void nativeSetReachability(boolean status);

  //  private native double nativeGetMetersPerPixelAtLatitude(long nativeMapViewPtr, double lat, double zoom);
//
//  private native ProjectedMeters nativeProjectedMetersForLatLng(long nativeMapViewPtr, double latitude,
//                                                                double longitude);
//
//  private native LatLng nativeLatLngForProjectedMeters(long nativeMapViewPtr, double northing, double easting);
//
//  private native PointF nativePixelForLatLng(long nativeMapViewPtr, double lat, double lon);
//
//  private native LatLng nativeLatLngForPixel(long nativeMapViewPtr, float x, float y);
//
//  private native double nativeGetTopOffsetPixelsForAnnotationSymbol(long nativeMapViewPtr, String symbolName);
//
//  private native double[] nativeGetCameraValues(long nativeMapViewPtr);
//
//  private native long nativeGetTransitionDuration(long nativeMapViewPtr);
//
//  private native void nativeSetTransitionDuration(long nativeMapViewPtr, long duration);
//
//  private native long nativeGetTransitionDelay(long nativeMapViewPtr);
//
//  private native void nativeSetTransitionDelay(long nativeMapViewPtr, long delay);
//
//  private native Layer nativeGetLayer(long nativeMapViewPtr, String layerId);
//
//  private native void nativeAddLayer(long nativeMapViewPtr, long layerPtr, String before);
//
//  private native void nativeRemoveLayerById(long nativeMapViewPtr, String layerId) throws NoSuchLayerException;
//
//  private native void nativeRemoveLayer(long nativeMapViewPtr, long layerId) throws NoSuchLayerException;
//
//  private native Source nativeGetSource(long nativeMapViewPtr, String sourceId);
//
//  private native void nativeAddSource(long nativeMapViewPtr, long nativeSourcePtr);
//
//  private native void nativeRemoveSourceById(long nativeMapViewPtr, String sourceId) throws NoSuchSourceException;
//
//  private native void nativeRemoveSource(long nativeMapViewPtr, long sourcePtr) throws NoSuchSourceException;
//
//  private native void nativeAddImage(long nativeMapViewPtr, String name, int width, int height, float pixelRatio,
//                                     byte[] array);
//
//  private native void nativeRemoveImage(long nativeMapViewPtr, String name);
//
//  private native void nativeUpdatePolygon(long nativeMapViewPtr, long polygonId, Polygon polygon);
//
//  private native void nativeUpdatePolyline(long nativeMapviewPtr, long polylineId, Polyline polyline);
//
  private native void nativeScheduleSnapshot();

  //
//  private native Feature[] nativeQueryRenderedFeaturesForPoint(long nativeMapViewPtr, float x, float y, String[]
//    layerIds);
//
//  private native Feature[] nativeQueryRenderedFeaturesForBox(long nativeMapViewPtr, float left, float top, float right,
//                                                             float bottom, String[] layerIds);
//
  private native void nativeSetAPIBaseUrl(String baseUrl);

  private native void nativeEnableFps(boolean enable);

  int getWidth() {
    return mapView.getWidth();
  }

  int getHeight() {
    return mapView.getHeight();
  }

  //
  // MapChangeEvents
  //

  void addOnMapChangedListener(@NonNull MapView.OnMapChangedListener listener) {
    onMapChangedListeners.add(listener);
  }

  void removeOnMapChangedListener(@NonNull MapView.OnMapChangedListener listener) {
    onMapChangedListeners.remove(listener);
  }

  //
  // Snapshot
  //

  void addSnapshotCallback(@NonNull MapboxMap.SnapshotReadyCallback callback) {
    snapshotReadyCallback = callback;
    scheduleTakeSnapshot();
    render();
  }
}
