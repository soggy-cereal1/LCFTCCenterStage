package com.example.meepmeeptesting;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.noahbres.meepmeep.MeepMeep;
import java.io.*;
import java.awt.*;
import javax.imageio.ImageIO;
import com.noahbres.meepmeep.roadrunner.DefaultBotBuilder;
import com.noahbres.meepmeep.roadrunner.entity.RoadRunnerBotEntity;

public class MeepMeepTesting {
    static int isBlue = -1;
    static int itemSector = 2;

    public static void main(String[] args) {
        int itemSector = 2;
        Pose2d pose2 = new Pose2d(12,-43, Math.toRadians(90+((0-1)*-39.4)));
        MeepMeep meepMeep = new MeepMeep(700);

        RoadRunnerBotEntity myBot = new DefaultBotBuilder(meepMeep)
                // Set bot constraints: maxVel, maxAccel, maxAngVel, maxAngAccel, track width
                .setConstraints(60, 60, Math.toRadians(180), Math.toRadians(180), 15)
                .followTrajectorySequence(drive ->
                        drive.trajectorySequenceBuilder(new Pose2d(-34, isBlue * 45, Math.toRadians((-isBlue * 90) - 10)))
                                .lineToLinearHeading(new Pose2d(-36, 36*isBlue, Math.toRadians(180))) // go to center of the tape
                                .lineToConstantHeading(new Vector2d(12, 36*isBlue)) // cross the field (go under truss if this is far)
                                .splineToSplineHeading(new Pose2d(48, 38*isBlue, Math.toRadians(180)), Math.toRadians(0))
                                .build()
                );
        meepMeep.setBackground(MeepMeep.Background.FIELD_CENTERSTAGE_JUICE_DARK)
                .setDarkMode(true)
                // Background opacity from 0-1
                .setBackgroundAlpha(0.95f)
                .addEntity(myBot)
                .start();
    }
}