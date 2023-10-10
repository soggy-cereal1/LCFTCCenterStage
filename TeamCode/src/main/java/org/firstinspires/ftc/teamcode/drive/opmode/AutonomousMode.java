package org.firstinspires.ftc.teamcode.drive.opmode;

import android.annotation.SuppressLint;
import android.graphics.Color;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.acmerobotics.roadrunner.trajectory.TrajectoryBuilder;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.NormalizedColorSensor;
import com.qualcomm.robotcore.hardware.NormalizedRGBA;
import com.qualcomm.robotcore.hardware.SwitchableLight;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AngularVelocity;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.drive.opmode.vision.testEOCVpipeline;
import org.firstinspires.ftc.teamcode.trajectorysequence.TrajectorySequence;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.VisionProcessor;
import org.firstinspires.ftc.vision.apriltag.AprilTagGameDatabase;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import java.util.*;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;


@Autonomous
public class AutonomousMode extends LinearOpMode {
    VisionPortal.Builder vPortalBuilder;
    VisionPortal vPortal;

    AprilTagProcessor aprilTagProcessor;
    AprilTagProcessor.Builder aprilTagProcessorBuilder;
    testEOCVpipeline detector = new testEOCVpipeline();
    //    TODO: Use dead wheels
    SampleMecanumDrive drive;
    //TODO: Update Constants to be 100% accurate (ex. wheel radius)
    IMU imu;

    NormalizedColorSensor colorSensor;


    DistanceSensor sensorDistance;
    int status;
    int itemSector;
    Pose2d startPose = new Pose2d(-36,-60, Math.toRadians(90));
    double detX;
    double distForward;

    @Override
    public void runOpMode() throws InterruptedException {
        aprilTagProcessor = initAprilTag();
        vPortal = initVisionPortal();

        setupIMU();

        setupColorSensor();
        setupDistanceSensor();

        drive = new SampleMecanumDrive(hardwareMap);
        // TODO: Fix Drive Constants physical measurements!!!
//        TODO: Move Reverse to here.

        Thread telemetryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted() && opModeIsActive()) {
                    outputTelemetry();

                    try {
                        Thread.sleep(10); // Introducing a small delay to prevent excessive updates
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });

        waitForStart();

        telemetryThread.start(); // Starting telemetry thread

        if (opModeIsActive()) {
            while (opModeIsActive()) {
                opModeLoop();
            }
        }

        telemetryThread.interrupt(); // Make sure to interrupt the telemetry thread when opMode is no longer active
    }

    private AprilTagProcessor initAprilTag() {
        aprilTagProcessorBuilder = new AprilTagProcessor.Builder();
        aprilTagProcessorBuilder.setTagLibrary(AprilTagGameDatabase.getCurrentGameTagLibrary());

        return aprilTagProcessorBuilder.build();
    }
    //
    private VisionPortal initVisionPortal() {
        vPortalBuilder = new VisionPortal.Builder();
        vPortalBuilder.setCamera(hardwareMap.get(WebcamName.class, "webcam"));
        vPortalBuilder.addProcessors(detector, aprilTagProcessor);

        return vPortalBuilder.build();
    }

    private void setupIMU() {
        imu = hardwareMap.get(IMU.class, "imu");
        RevHubOrientationOnRobot.LogoFacingDirection logoDirection = RevHubOrientationOnRobot.LogoFacingDirection.UP;
        RevHubOrientationOnRobot.UsbFacingDirection  usbDirection  = RevHubOrientationOnRobot.UsbFacingDirection.FORWARD;
        RevHubOrientationOnRobot orientationOnRobot = new RevHubOrientationOnRobot(logoDirection, usbDirection);
        imu.initialize(new IMU.Parameters(orientationOnRobot));
    }

    private void setupColorSensor() {
        colorSensor = hardwareMap.get(NormalizedColorSensor.class, "sensor_color");
        if (colorSensor instanceof SwitchableLight) {
            ((SwitchableLight)colorSensor).enableLight(true);
        }

        colorSensor.setGain(40);
    }

    private void setupDistanceSensor() {
        sensorDistance = hardwareMap.get(DistanceSensor.class, "sensor_distance");
    }
    private void opModeLoop() {
        switch(status) {
            case 0: runPieceDetector();
                break;
            case 1: driveToLine();
                break;
            case 2: alignLine();
                break;
            case 3: dropPixel();
                break;
            case 4: crossField();
                break;
            case 5: findTargetTag();
                break;
            case 6: fixDistance();
                break;
            case 7: scorePixel();
                break;
            case 8: park();
                break;
        }
    }
    private void driveToLine() {
        TrajectorySequence traj1;
        traj1 = drive.trajectorySequenceBuilder(startPose)
                .forward(32)
                .turn(Math.toRadians(180-(itemSector*90)))
                .build();
        drive.followTrajectorySequence(traj1);
        status++;

    }
    //    }
    private void alignLine() { // get pose estimate, add second one
        double redValue =  colorSensor.getNormalizedColors().red;
        double blueValue = colorSensor.getNormalizedColors().blue;

        telemetry.addData("Red Value (0 to 1)", "%4.2f", redValue);
        telemetry.addData("Blue Value (0 to 1)", "%4.2f", blueValue);
        telemetry.update();

        if (redValue > 0.4 || blueValue > 0.5) {
            // We found a line (either red or blue)
            drive.setMotorPowers(0, 0, 0, 0); // Stop the robot
        } else {
            // Continue moving forward if no line is detected
            Trajectory myTrajectory = drive.trajectoryBuilder(new Pose2d())
                    .forward(3)
                    .build();
            drive.followTrajectory(myTrajectory);
        }
    }
    private void crossField() {
        // cross the field.
    }
    private void park() {
        // park.
    }
    private void dropPixel() {
        // we don't have a scoring mechanism yet
    }
    private void scorePixel() {
        // see above
    }

    private void runPieceDetector() {
        // R is 0, M is 1, L is 2
        vPortal.setProcessorEnabled(detector, true);
        boolean stop = false;
        while (!stop) {
            if (detector.locationInt() != 0) {
                itemSector = detector.locationInt();
                //TODO: run a couple times, area of mask is sufficient, find most common of 20 or so frames
                stop = true;
                status = 1;
                vPortal.stopStreaming();
            }
        }
    }
    private void findTargetTag() {
        vPortal.resumeStreaming();
        vPortal.setProcessorEnabled(aprilTagProcessor, true);
        List<AprilTagDetection> currentDetections = aprilTagProcessor.getDetections();
        for (AprilTagDetection detection : currentDetections) {
            if (detection.id % 3 == itemSector || detection.id % 3 == itemSector-3) {
                detX = detection.ftcPose.x;
                if(detX !=0) {
                    if (detX > 0) {
                        drive.setMotorPowers(0.1,-0.1,0.1,-0.1);
                    } else {
                        drive.setMotorPowers(-0.1,0.1,-0.1,0.1);
                    }

                } else {
                    vPortal.close();
                    break;
                }
            }
        }
    }
    private void fixDistance() {
        if (sensorDistance.getDistance(DistanceUnit.INCH) != DistanceUnit.infinity) {
            distForward = sensorDistance.getDistance(DistanceUnit.INCH);
        } else {
            distForward = 12.75;
        }
        if (distForward != 12.75) {
            double i = 12.75-distForward;
            drive.setMotorPowers(i, i, i, i);
        }
    }

    private void outputTelemetry() {
        // TODO: Also output to .log file.
//        telemetry.addLine("---------April Tag Data----------");
//        aprilTagTelemetry();
        telemetry.addLine(String.valueOf(itemSector));
        telemetry.addData("status: ", status);
        telemetry.addLine("---------IMU Data----------");
        IMUTelemetry();
        telemetry.addLine("---------Pose Data----------");
//        TODO: Add beysian estimate. Kalman filter.
        poseTelemetry();
        telemetry.addLine("---------Color Data----------");
        colorSensorTelemetry();
        telemetry.addLine("---------Distance Sensor----------");
        distanceSensorTelemetry();
    }

    @SuppressLint("DefaultLocale")
    private void distanceSensorTelemetry() {
        telemetry.addData("range", String.format("%.01f mm", sensorDistance.getDistance(DistanceUnit.MM)));
    }

    private void colorSensorTelemetry() {
        NormalizedRGBA colors = colorSensor.getNormalizedColors();
        telemetry.addLine()
                .addData("Red", "%.3f", colors.red)
                .addData("Green", "%.3f", colors.green)
                .addData("Blue", "%.3f", colors.blue);

    }

    private void poseTelemetry() {
        telemetry.addLine(String.format("Estimated Pose: %s", drive.getPoseEstimate().toString()));
    }

    @SuppressLint("DefaultLocale")
    private void aprilTagTelemetry() {
        List<AprilTagDetection> currentDetections = aprilTagProcessor.getDetections();
        telemetry.addData("# AprilTags Detected", currentDetections.size());

        // Step through the list of detections and display info for each one.
        for (AprilTagDetection detection : currentDetections) {
            if (detection.metadata != null) {
                telemetry.addLine(String.format("\n==== (ID %d) %s", detection.id, detection.metadata.name));
                telemetry.addLine(String.format("XYZ %6.1f %6.1f %6.1f  (inch)", detection.ftcPose.x, detection.ftcPose.y, detection.ftcPose.z));
                telemetry.addLine(String.format("PRY %6.1f %6.1f %6.1f  (deg)", detection.ftcPose.pitch, detection.ftcPose.roll, detection.ftcPose.yaw));
                telemetry.addLine(String.format("RBE %6.1f %6.1f %6.1f  (inch, deg, deg)", detection.ftcPose.range, detection.ftcPose.bearing, detection.ftcPose.elevation));
            } else {
                telemetry.addLine(String.format("\n==== (ID %d) Unknown", detection.id));
                telemetry.addLine(String.format("Center %6.0f %6.0f   (pixels)", detection.center.x, detection.center.y));
            }
        }   // end for() loop
    }


    private void IMUTelemetry() {
//        TODO: create IMU Class.
        // Retrieve Rotational Angles and Velocities
        YawPitchRollAngles orientation = imu.getRobotYawPitchRollAngles();
        AngularVelocity angularVelocity = imu.getRobotAngularVelocity(AngleUnit.DEGREES);

        telemetry.addData("Yaw (Z)", "%.2f Deg. (Heading)", orientation.getYaw(AngleUnit.DEGREES));
        telemetry.addData("Pitch (X)", "%.2f Deg.", orientation.getPitch(AngleUnit.DEGREES));
        telemetry.addData("Roll (Y)", "%.2f Deg.\n", orientation.getRoll(AngleUnit.DEGREES));
        telemetry.addData("Yaw (Z) velocity", "%.2f Deg/Sec", angularVelocity.zRotationRate);
        telemetry.addData("Pitch (X) velocity", "%.2f Deg/Sec", angularVelocity.xRotationRate);
        telemetry.addData("Roll (Y) velocity", "%.2f Deg/Sec", angularVelocity.yRotationRate);
        telemetry.update();
    }

}