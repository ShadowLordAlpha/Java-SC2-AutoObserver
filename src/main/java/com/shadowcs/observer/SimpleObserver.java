package com.shadowcs.observer;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.ReplayInfo;
import com.shadowcs.observer.camera.CameraModuleObserver;

import java.io.IOException;
import java.nio.file.Paths;

public class SimpleObserver extends S2ReplayObserver {

    private long lastFrame;

    public static void main(String...args) throws IOException {

        S2ReplayObserver observer = new SimpleObserver();

        var path = Paths.get("./replays").toAbsolutePath().toString();

        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setProcessPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
                .setDataVersion("B89B5D6FA7CBF6452E721311BFBC6CB2")

                .addReplayObserver(observer)
                .setReplayRecovery(true)
                //.setReplayPath(Paths.get("./replays/")) // use your own folder of replays, they will go one after the other
                .setReplayPath(Paths.get(path)) // use your own folder of replays, they will go one after the other
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

    public SimpleObserver() {
        observer = new CameraModuleObserver(this);
    }

    @Override
    public void onGameStart() {
        observer.onStart();
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
        observer.moveCameraUnitCreated(unitInPool.unit());
    }

    @Override
    public void onStep() {
        try {

            control().observerAction()
            observation();
            observation().getChatMessages();
            observer.onFrame();
            double delay = System.currentTimeMillis() - lastFrame;
            double sleeptime = 1000.0 / (22.4 * 2);

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
        return false;
    }
}
