package io.fotoapparat;

import android.content.Context;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.fotoapparat.hardware.CameraDevice;
import io.fotoapparat.hardware.orientation.OrientationSensor;
import io.fotoapparat.hardware.orientation.RotationListener;
import io.fotoapparat.hardware.orientation.ScreenOrientationProvider;
import io.fotoapparat.parameter.provider.CapabilitiesProvider;
import io.fotoapparat.parameter.provider.InitialParametersProvider;
import io.fotoapparat.result.CapabilitiesResult;
import io.fotoapparat.result.PhotoResult;
import io.fotoapparat.routine.AutoFocusRoutine;
import io.fotoapparat.routine.ConfigurePreviewStreamRoutine;
import io.fotoapparat.routine.StartCameraRoutine;
import io.fotoapparat.routine.StopCameraRoutine;
import io.fotoapparat.routine.TakePictureRoutine;
import io.fotoapparat.routine.UpdateOrientationRoutine;

/**
 * Camera. Takes pictures.
 */
public class Fotoapparat {

    private static final Executor SERIAL_EXECUTOR = Executors.newSingleThreadExecutor();

    private final StartCameraRoutine startCameraRoutine;
    private final StopCameraRoutine stopCameraRoutine;
    private final UpdateOrientationRoutine updateOrientationRoutine;
    private final ConfigurePreviewStreamRoutine configurePreviewStreamRoutine;
    private final CapabilitiesProvider capabilitiesProvider;
    private final TakePictureRoutine takePictureRoutine;
    private final AutoFocusRoutine autoFocusRoutine;
    private final Executor executor;

    private boolean started = false;

    Fotoapparat(StartCameraRoutine startCameraRoutine,
                StopCameraRoutine stopCameraRoutine,
                UpdateOrientationRoutine updateOrientationRoutine,
                ConfigurePreviewStreamRoutine configurePreviewStreamRoutine,
                CapabilitiesProvider capabilitiesProvider,
                TakePictureRoutine takePictureRoutine,
                AutoFocusRoutine autoFocusRoutine,
                Executor executor) {
        this.startCameraRoutine = startCameraRoutine;
        this.stopCameraRoutine = stopCameraRoutine;
        this.updateOrientationRoutine = updateOrientationRoutine;
        this.configurePreviewStreamRoutine = configurePreviewStreamRoutine;
        this.capabilitiesProvider = capabilitiesProvider;
        this.takePictureRoutine = takePictureRoutine;
        this.autoFocusRoutine = autoFocusRoutine;
        this.executor = executor;
    }

    public static FotoapparatBuilder with(Context context) {
        if (context == null) {
            throw new IllegalStateException("Context is null.");
        }

        return new FotoapparatBuilder(context);
    }

    static Fotoapparat create(FotoapparatBuilder builder) {

        CameraDevice cameraDevice = builder.cameraProvider.get(builder.logger);
        ScreenOrientationProvider screenOrientationProvider = new ScreenOrientationProvider(builder.context);
        RotationListener rotationListener = new RotationListener(builder.context);
        InitialParametersProvider initialParametersProvider = new InitialParametersProvider(
                cameraDevice,
                builder.photoSizeSelector,
                builder.previewSizeSelector,
                builder.focusModeSelector,
                builder.flashSelector
        );

        StartCameraRoutine startCameraRoutine = new StartCameraRoutine(
                builder.availableLensPositionsProvider,
                cameraDevice,
                builder.renderer,
                builder.lensPositionSelector,
                screenOrientationProvider,
                initialParametersProvider
        );

        StopCameraRoutine stopCameraRoutine = new StopCameraRoutine(cameraDevice);

        OrientationSensor orientationSensor = new OrientationSensor(
                rotationListener,
                screenOrientationProvider
        );

        UpdateOrientationRoutine updateOrientationRoutine = new UpdateOrientationRoutine(
                cameraDevice,
                orientationSensor,
                SERIAL_EXECUTOR
        );

        ConfigurePreviewStreamRoutine configurePreviewStreamRoutine = new ConfigurePreviewStreamRoutine(
                cameraDevice,
                builder.frameProcessor
        );

        CapabilitiesProvider capabilitiesProvider = new CapabilitiesProvider(
                cameraDevice,
                SERIAL_EXECUTOR
        );

        TakePictureRoutine takePictureRoutine = new TakePictureRoutine(
                cameraDevice,
                SERIAL_EXECUTOR
        );

        AutoFocusRoutine autoFocusRoutine = new AutoFocusRoutine(cameraDevice);

        return new Fotoapparat(
                startCameraRoutine,
                stopCameraRoutine,
                updateOrientationRoutine,
                configurePreviewStreamRoutine,
                capabilitiesProvider,
                takePictureRoutine,
                autoFocusRoutine,
                SERIAL_EXECUTOR
        );
    }

    /**
     * Provides camera capabilities asynchronously, returns immediately.
     *
     * @return {@link CapabilitiesResult} which will deliver result asynchronously.
     */
    public CapabilitiesResult getCapabilities() {
        ensureStarted();

        return capabilitiesProvider.getCapabilities();
    }

    /**
     * Takes picture. Returns immediately.
     *
     * @return {@link PhotoResult} which will deliver result asynchronously.
     */
    public PhotoResult takePicture() {
        ensureStarted();

        return takePictureRoutine.takePicture();
    }

    /**
     * Performs auto focus. If it is not available or not enabled, does nothing.
     */
    public Fotoapparat autoFocus() {
        ensureStarted();

        executor.execute(
                autoFocusRoutine
        );

        return this;
    }

    /**
     * Starts camera.
     *
     * @throws IllegalStateException if camera was already started.
     */
    public void start() {
        ensureNotStarted();
        started = true;

        startCamera();
        configurePreviewStream();
        updateOrientationRoutine.start();
    }

    /**
     * Stops camera.
     *
     * @throws IllegalStateException if camera is not started.
     */
    public void stop() {
        ensureStarted();
        started = false;

        updateOrientationRoutine.stop();
        stopCamera();
    }

    private void startCamera() {
        executor.execute(
                startCameraRoutine
        );
    }

    private void stopCamera() {
        executor.execute(
                stopCameraRoutine
        );
    }

    private void configurePreviewStream() {
        executor.execute(
                configurePreviewStreamRoutine
        );
    }

    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException("Camera is not started!");
        }
    }

    private void ensureNotStarted() {
        if (started) {
            throw new IllegalStateException("Camera is already started!");
        }
    }
}
