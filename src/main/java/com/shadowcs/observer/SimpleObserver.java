package com.shadowcs.observer;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.ReplayInfo;
import com.shadowcs.observer.camera.CameraModuleObserver;
import picocli.CommandLine;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class SimpleObserver extends S2ReplayObserver {

    private static ReplaySettings replaySettings = new ReplaySettings();

    public static final int SPEED_NORMAL = 1;
    public static final int SPEED_FASTER = 2;
    public static final int SPEED_FASTEST = 4;
    public static final int SPEED_LUDICROUS = 8;

    private float speed;
    private int rotate;
    private Robot r;
    private int delay;
    private boolean toggle;
    private long lastFrame;
    private long rotateFrame;
    private int rotation = 0;

    private static int[] keylist = {
            KeyEvent.VK_R,
            KeyEvent.VK_I,
            KeyEvent.VK_S,
            KeyEvent.VK_U,
            KeyEvent.VK_L,
            KeyEvent.VK_D,
            KeyEvent.VK_A,
            KeyEvent.VK_M,
    };

    public static void main(String...args) throws IOException {

        CommandLine cli = new CommandLine(replaySettings).setUnmatchedArgumentsAllowed(true);
        cli.parse(args);
        CommandLine.usage(replaySettings, System.out);
        String[] passArgs = cli.getUnmatchedArguments().toArray(new String[0]);

        S2ReplayObserver observer = new SimpleObserver(replaySettings.speed(), replaySettings.delay(), replaySettings.rotate(), replaySettings.production());

        File file = new File("./replays");
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(passArgs)
                //.setProcessPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
                .setDataVersion(replaySettings.data())
                .addReplayObserver(observer)
                .setReplayRecovery(true)
                .setReplayPath(replaySettings.replayPath() == null ? file.getAbsoluteFile().toPath(): replaySettings.replayPath()) // use your own folder of replays, they will go one after the other
                .launchStarcraft();

        if(s2Coordinator.hasReplays()) {

            while (s2Coordinator.update() && !s2Coordinator.allGamesEnded()) {

            }
        } else {
            System.out.println("No Replays Found!");
        }

        s2Coordinator.quit();
    }

    private CameraModuleObserver observer;

    public SimpleObserver(float speed, int delay, int rotate, boolean toggle) {
        this.speed = speed;
        this.delay = delay;
        this.rotate = rotate;
        this.toggle = toggle;

        try {
            r = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }

        observer = new CameraModuleObserver(this);
    }

    public void robotLoop(int key) {
        new Thread(() -> {
            try {
                r.keyPress(key);
                r.delay(100);
                r.keyRelease(key);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onGameStart() {

        rotateFrame = System.currentTimeMillis();
        if(toggle) {
            robotLoop(KeyEvent.VK_D);
        }

        observer.onStart();
    }

    @Override
    public void onGameEnd() {

        try {
            if(delay > 0) {
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
        observer.moveCameraUnitCreated(unitInPool.unit());
    }

    @Override
    public void onStep() {
        try {

            if(rotate > 0 && rotateFrame < System.currentTimeMillis()) {
                rotateFrame = System.currentTimeMillis() + rotate;
                robotLoop(keylist[(rotation++ % keylist.length)]);
            }

            observation().getChatMessages();
            observer.onFrame();
            double delay = System.currentTimeMillis() - lastFrame;
            double sleeptime = 1000.0 / (22.4 * speed);

            if((sleeptime - delay) > 0) {
                Thread.sleep(Math.round(sleeptime - delay));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        lastFrame = System.currentTimeMillis();
    }

    @Override
    public boolean ignoreReplay(ReplayInfo replayInfo, int playerId) {
        System.out.println(replayInfo);
        return false;
    }
}
