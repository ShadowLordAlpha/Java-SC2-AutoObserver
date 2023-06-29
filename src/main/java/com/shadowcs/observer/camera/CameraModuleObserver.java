package com.shadowcs.observer.camera;

import SC2APIProtocol.Sc2Api;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.protocol.request.Requests;
import com.github.ocraft.s2client.protocol.spatial.PointI;

public class CameraModuleObserver extends CameraModule {

    public CameraModuleObserver(S2ReplayObserver bot) {
        super(bot);
    }

    @Override
    public void onStart() {

        // This should work once it is implemented on the proto side. Once i actually figure it out...
        //Requests.observerActions(); // It has somthing to do with this one i believe

        //m_client().control().replayControl().proto().sendRequest(request);

        /*Sc2Api.RequestObserverAction
        Sc2Api.ActionObserverPlayerPerspective.Builder request = Sc2Api.ActionObserverPlayerPerspective.newBuilder();
        request.setPlayerId(0); // 0 = everyone
        m_client().control().replayControl().proto().sendRequest(request);

        sc2::GameRequestPtr request = m_observer->Control()->Proto().MakeRequest();
        SC2APIProtocol::RequestObserverAction* obsRequest = request->mutable_obs_action();
        SC2APIProtocol::ObserverAction* action = obsRequest->add_actions();
        SC2APIProtocol::ActionObserverPlayerPerspective * player_perspective = action->mutable_player_perspective();
        player_perspective->set_player_id(0);  // 0 = everyone
        m_client->Control()->Proto().SendRequest(request);
        m_client->Control()->WaitForResponse();*/

        super.onStart();
    }

    @Override
    public void onFrame() {
        super.onFrame();
    }

    @Override
    protected void updateCameraPositionExcecute() {
        m_client().control().observerAction().cameraMove(PointI.of((int) currentCameraPosition.getX(), (int) currentCameraPosition.getY()), cameraDistance);
    }
}
