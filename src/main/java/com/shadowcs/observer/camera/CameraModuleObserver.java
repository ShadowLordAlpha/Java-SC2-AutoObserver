package com.shadowcs.observer.camera;

import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CameraModuleObserver extends CameraModule {

    public CameraModuleObserver(S2ReplayObserver bot) {
        super(bot);
    }

    @Override
    public void onStart() {

        m_client().control().observerAction().cameraSetPerspective(0);
        super.onStart();
    }

    @Override
    public void onFrame() {
        super.onFrame();
    }

    @Override
    protected void updateCameraPositionExcecute() {
        //log.info("Moving Camera");

        if(followUnit()) {
            //log.info("Moving Camera to Unit");

            // Annoyingly this doesn't appear to work.... the other one does though for some reason
            // m_client().control().observerAction().cameraFollowUnits(cameraFocusUnit.unit());

            m_client().control().observerAction().cameraMove(cameraFocusUnit.unit().getPosition().toPoint2d(), cameraDistance);
        } else {
            //log.info("Moving Camera to Position {} {}", cameraFocusPosition.getX(), cameraFocusPosition.getY());

            m_client().control().observerAction().cameraMove(Point2d.of(cameraFocusPosition.getX(), cameraFocusPosition.getY()), cameraDistance);
        }
    }
}
