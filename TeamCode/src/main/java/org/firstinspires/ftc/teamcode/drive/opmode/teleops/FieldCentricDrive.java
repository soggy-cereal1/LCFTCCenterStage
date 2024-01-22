package org.firstinspires.ftc.teamcode.drive.opmode.teleops;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.teamcode.drive.opmode.subsystems.InputOutput;

@TeleOp
@Config
public class FieldCentricDrive extends LinearOpMode {
    boolean isDepressedUp = false;
    FtcDashboard dashboard;
    boolean isDepressedDown = false;
    boolean wasUpPressed;
    boolean wasDownPressed;
    boolean runningPID;
    public static double Kp = 0.625;
    public static double Ki = 0;
    public static double Kd = 0;
    double tolerance = Math.toRadians(3);
    double error;
    double integralSum=0;
    double lastError = 0;
    double derivative;
    double rx;

    //Sets value of Kp, Ki and Kd for PID controller
    public void setTunings(double kp, double ki, double kd) {
        Kp = kp;
        Ki = ki;
        Kd = kd;
    }
    public void buttonPressedUp() {
        if (gamepad2.dpad_up && !isDepressedUp) {
            isDepressedUp = true;
            wasUpPressed = false;
        }
        if (!gamepad2.dpad_up && isDepressedUp) {
            isDepressedUp = false;
            wasUpPressed = true;
        }
    }
    public void buttonPressedDown() {
        if (gamepad2.dpad_down && !isDepressedDown) {
            isDepressedDown = true;
            wasDownPressed = false;
        }
        if (!gamepad2.dpad_down && isDepressedDown) {
            isDepressedDown = false;
            wasDownPressed = true;
        }
    }
    @Override
    public void runOpMode() throws InterruptedException {
        InputOutput arm = new InputOutput(hardwareMap, true, .25, .15);
        // Declare our motors
        // Make sure your ID's match your configuration
        dashboard = FtcDashboard.getInstance();
        telemetry = dashboard.getTelemetry();
        DcMotor frontLeftMotor = hardwareMap.dcMotor.get("leftFront");
        DcMotor backLeftMotor = hardwareMap.dcMotor.get("leftRear");
        DcMotor frontRightMotor = hardwareMap.dcMotor.get("rightFront");
        DcMotor backRightMotor = hardwareMap.dcMotor.get("rightRear");

        DcMotor winch = hardwareMap.dcMotor.get("winch");
        Servo droneRelease = hardwareMap.servo.get("droneRelease");
        Servo hook = hardwareMap.servo.get("hook");
        ElapsedTime timer = new ElapsedTime(ElapsedTime.Resolution.SECONDS);

        // Reverse the right side motors. This may be wrong for your setup.
        // If your robot moves backwards when commanded to go forwards,
        // reverse the left side instead.
        // See the note about this earlier on this page.
        frontRightMotor.setDirection(DcMotorSimple.Direction.REVERSE);
        backRightMotor.setDirection(DcMotorSimple.Direction.REVERSE);

        // Retrieve the IMU from the hardware map
        IMU imu = hardwareMap.get(IMU.class, "imu");
        // Adjust the orientation parameters to match your robot
        IMU.Parameters parameters = new IMU.Parameters(new RevHubOrientationOnRobot(
                //CHANGE THESE ONCE ORIENTATION IS KNOW
                RevHubOrientationOnRobot.LogoFacingDirection.LEFT,
                RevHubOrientationOnRobot.UsbFacingDirection.UP));
        // Without this, the REV Hub's orientation is assumed to be logo up / USB forward
        imu.initialize(parameters);
        frontLeftMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        frontRightMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backLeftMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backRightMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        arm.grab();
        waitForStart();

        if (isStopRequested()) return;

        while (opModeIsActive()) {
            double y = -gamepad1.left_stick_y; // Y stick value is reversed
            double x = gamepad1.left_stick_x;
            if (!runningPID) { rx = -gamepad1.right_stick_x; }

            // This button choice was made so that it is hard to hit on accident,
            // it can be freely changed based on preference..
            if (gamepad1.options) {
                imu.resetYaw();
            }

            double botHeading = imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);

            if(gamepad1.back) {
                runningPID = true;
            }
            //PID controller for heading
            if (runningPID) {
                if (Math.abs(botHeading) < tolerance){
                    runningPID =  false;
                }
                else {
                    error = -botHeading;
                    integralSum = integralSum + (error * timer.seconds());
                    derivative = (error - lastError)/timer.seconds();

                    rx = (Kp * error) + (Ki * integralSum) + (Kd * derivative);

                    lastError = error;

                    timer.reset();
                }
            }

            // Rotate the movement direction counter to the bots rotation
            double rotX = x * Math.cos(-botHeading) - y * Math.sin(-botHeading);
            double rotY = x * Math.sin(-botHeading) + y * Math.cos(-botHeading);

            rotX = rotX * 1.1;  // Counteract imperfect strafing

            // Denominator is the largest motor power (absolute value) or 1
            // This ensures all the powers maintain the same ratio,
            // but only if at least one is out of the range [-1, 1]
            double denominator = Math.max(Math.abs(rotY) + Math.abs(rotX) + Math.abs(rx), 1);
            double frontLeftPower = (rotY + rotX + rx) / denominator;
            double backLeftPower = (rotY - rotX + rx) / denominator;
            double frontRightPower = (rotY - rotX - rx) / denominator;
            double backRightPower = (rotY + rotX - rx) / denominator;

            frontLeftMotor.setPower(frontLeftPower);
            backLeftMotor.setPower(backLeftPower);
            frontRightMotor.setPower(frontRightPower);
            backRightMotor.setPower(backRightPower);



            buttonPressedDown();
            buttonPressedUp();
            if (wasUpPressed) {
               arm.goTo(arm.getLevel() + 1);
               wasUpPressed = false;
            } else if (wasDownPressed) {
               arm.goTo(arm.getLevel() - 1);
               wasDownPressed = false;
            }
            if (gamepad2.dpad_right) {
                arm.goTo(0);
            }
            if (gamepad2.x) {
                arm.ground();
            } else if (gamepad2.y) {
                arm.rest();
            } else if (gamepad2.b) {
                arm.board();
            }

           if (gamepad2.left_bumper) {
                arm.release();
            } else if (gamepad2.right_bumper) {
              arm.grab();
           }

           if (gamepad2.back) {
               droneRelease.setPosition(1);
           }

            if (timer.time() > 90) {
                hook.setPosition(0);
            }

            if (timer.time() > 90 && gamepad2.right_trigger > .5) {
                winch.setPower(1);
            } else if (timer.time() > 90 && gamepad2.left_trigger > .5) {
                winch.setPower(-1);
            } else {
                winch.setPower(0);
            }
            arm.update();
            telemetry.addData("botheading", botHeading);
            telemetry.addData("rx", rx);
            telemetry.update();
            // PID Controller for Heading
            // Constants need to be tuned

    }
}
}