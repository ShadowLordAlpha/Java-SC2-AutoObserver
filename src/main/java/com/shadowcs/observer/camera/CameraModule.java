package com.shadowcs.observer.camera;

import com.github.ocraft.s2client.api.S2Client;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.PlayerType;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import lombok.Data;

import java.util.*;

import static java.util.Arrays.asList;

@Data
public abstract class CameraModule {

    // Radius to detect groups of army units
    public static final float armyBlobRadius = 10.0f;
    // Movement speed of camera
    public static final float moveFactor = 0.1f;
    // Only smooth movemnt when closer as...
    public static final float cameraJumpThreshold = 30.0f;
    // When is a unit near a start location
    public static final float nearStartLocationDistance = 50.0f;
    // Camera distance to map (zoom). 0 means default.
    public static final float cameraDistance = 50.0f;

    private static final Set<UnitType> FILTER_TWONCENTER = new HashSet<>(asList(
            Units.ZERG_HATCHERY,
            Units.ZERG_LAIR,
            Units.ZERG_HIVE,

            Units.PROTOSS_NEXUS,

            Units.TERRAN_COMMAND_CENTER,
            Units.TERRAN_ORBITAL_COMMAND,
            Units.TERRAN_PLANETARY_FORTRESS
    ));

    //Sometimes getObservation fails when starting a game.
    protected boolean m_initialized;

    protected S2ReplayObserver m_client;
    protected Set<Integer> m_playerIDs = new HashSet<>();
    protected Map<Integer, Point2d> m_startLocations = new HashMap<>();

    protected int cameraMoveTime;
    protected int cameraMoveTimeMin;
    protected int watchScoutWorkerUntil;

    protected long lastMoved;
    protected int lastMovedPriority;
    protected Point2d lastMovedPosition;
    protected Point2d currentCameraPosition;
    protected Point2d cameraFocusPosition;
    protected UnitInPool cameraFocusUnit;
    protected boolean followUnit;

    public CameraModule(S2ReplayObserver bot) {
        m_initialized(false);
        m_client(bot);
        cameraMoveTime(200);
        cameraMoveTimeMin(75);
        watchScoutWorkerUntil(7500);
        lastMoved(0);
        lastMovedPriority(0);
        lastMovedPosition(Point2d.of(0, 0));
        cameraFocusPosition(Point2d.of(0, 0));
        cameraFocusUnit(null);
        followUnit(false);
    }
    public void onStart() {
        if (lastMoved > 0) {
            lastMoved = 0;
            lastMovedPriority = 0;
            lastMovedPosition = Point2d.of(0, 0);
            cameraFocusUnit = null;
            followUnit = false;
        }
        setPlayerIds();
        setPlayerStartLocations();
        cameraFocusPosition = m_startLocations.values().stream().findFirst().orElse(lastMovedPosition);
        currentCameraPosition = m_startLocations.values().stream().findFirst().orElse(lastMovedPosition);
        m_initialized = true;
    }
    public void onFrame() {
        // Sometimes the first GetObservation() fails...
        if (!m_initialized) {
            onStart();
            return;
        }
        moveCameraFallingNuke();
        moveCameraNukeDetect();
        // moveCameraIsUnderAttack();
        moveCameraIsAttacking();
        if (m_client.observation().getGameLoop() <= watchScoutWorkerUntil) {
            moveCameraScoutWorker();
        }
        moveCameraDrop();
        moveCameraArmy();

        updateCameraPosition();
    }
    public void moveCameraUnitCreated(Unit unit) {
        if (!m_initialized) {
            return;
        }
        int prio;
        if (isBuilding(unit.getType())) {
            prio = 2;
        } else {
            prio = 1;
        }

        if (!shouldMoveCamera(prio) || unit.getType() == Units.TERRAN_KD8CHARGE || unit.getType() == Units.ZERG_LARVA || unit.getType() == Units.PROTOSS_INTERCEPTOR) {
            return;
        } else if (!isWorkerType(unit.getType())) {
            moveCamera(unit.getPosition().toPoint2d(), prio);
        }
    }

    //Depending on whether we have an agent or an observer we need to use different functions to move the camera
    protected abstract void updateCameraPositionExcecute();

    protected void moveCamera(Point2d pos, int priority) {
        if (!shouldMoveCamera(priority)) {
            return;
        }
        if (followUnit == false && cameraFocusPosition == pos) {
            // don't register a camera move if the position is the same
            return;
        }

        cameraFocusPosition = pos;
        lastMovedPosition = cameraFocusPosition;
        lastMoved = m_client.observation().getGameLoop();
        lastMovedPriority = priority;
        followUnit = false;
    }
    protected void moveCamera(UnitInPool unit, int priority) {
        if (!shouldMoveCamera(priority)) {
            return;
        }
        if (followUnit == true && cameraFocusUnit == unit) {
            // don't register a camera move if we follow the same unit
            return;
        }

        cameraFocusUnit = unit;
        lastMovedPosition = cameraFocusUnit.unit().getPosition().toPoint2d();
        lastMoved = m_client.observation().getGameLoop();
        lastMovedPriority = priority;
        followUnit = true;
    }
    protected void moveCameraIsAttacking() {
        int prio = 4;
        if (!shouldMoveCamera(prio)) {
            return;
        }

        for (var unit : m_client.observation().getUnits()) {
            if (isAttacking(unit.unit())) {
                moveCamera(unit, prio);
            }
        }
    }
    protected void moveCameraIsUnderAttack() {
        int prio = 4;
        if (!shouldMoveCamera(prio)) {
            return;
        }

        for (var unit: m_client.observation().getUnits()) {
            if (isUnderAttack(unit.unit())) {
                moveCamera(unit, prio);
            }
        }
    }
    protected void moveCameraScoutWorker() {
        int highPrio = 2;
	    int lowPrio = 0;
        if (!shouldMoveCamera(lowPrio)) {
            return;
        }

        for (var unit: m_client.observation().getUnits(u -> isWorkerType(u.unit().getType()))) {

            if (isNearOpponentStartLocation(unit.unit().getPosition().toPoint2d(), unit.unit().getOwner())) {
                moveCamera(unit, highPrio);
            } else if (!isNearOwnStartLocation(unit.unit().getPosition().toPoint2d(), unit.unit().getOwner())) {
                moveCamera(unit, lowPrio);
            }
        }
    }
    protected void moveCameraFallingNuke() {
        int prio = 6;
        if (!shouldMoveCamera(prio)) {
            return;
        }
        for (var unit: m_client.observation().getUnits()) {
            if (unit.unit().getType() == Units.TERRAN_NUKE) {
                moveCamera(unit, prio);
                return;
            }
        }
    }
    protected void moveCameraNukeDetect() {
        int prio = 5;
        if (!shouldMoveCamera(prio)) {
            return;
        }

        for (var effects: m_client.observation().getEffects()) {
            if (effects.getEffect() == Effects.NUKE_PERSISTENT) {
                moveCamera(effects.getPositions().stream().findFirst().get(), prio);
                return;
            }
        }
    }
    protected void moveCameraDrop() {
        int prio = 3;
        if (!shouldMoveCamera(prio)) {
            return;
        }
        for (var unit: m_client.observation().getUnits()) {
            if ((unit.unit().getType() == Units.ZERG_OVERLORD_TRANSPORT || unit.unit().getType() == Units.TERRAN_MEDIVAC || unit.unit().getType() == Units.PROTOSS_WARP_PRISM)
                    && isNearOpponentStartLocation(unit.unit().getPosition().toPoint2d(), unit.unit().getOwner()) && unit.unit().getCargoSpaceTaken().orElse(0) > 0) {
                moveCamera(unit, prio);
            }
        }
    }
    protected void moveCameraArmy() {
        int prio = 1;
        if (!shouldMoveCamera(prio)) {
            return;
        }
        // Double loop, check if army units are close to each other

        Point2d bestPos;
	    UnitInPool bestPosUnit = null;
        int mostUnitsNearby = 0;

        for (var unit : m_client.observation().getUnits()) {
            if (!isArmyUnitType(unit.unit().getType()) || unit.unit().getDisplayType() != DisplayType.VISIBLE) {
                continue;
            }

            Point2d uPos = unit.unit().getPosition().toPoint2d();

            int nrUnitsNearby = 0;
            for (var nearbyUnit: m_client.observation().getUnits()) {
                if (!isArmyUnitType(nearbyUnit.unit().getType()) || unit.unit().getDisplayType() != DisplayType.VISIBLE || dist(unit.unit(), nearbyUnit.unit()) > armyBlobRadius) {
                    continue;
                }
                nrUnitsNearby++;
            }
            if (nrUnitsNearby > mostUnitsNearby) {
                mostUnitsNearby = nrUnitsNearby;
                bestPos = uPos;
                bestPosUnit = unit;
            }
        }

        if (mostUnitsNearby > 1) {
            moveCamera(bestPosUnit, prio);
        }
    }

    /**
     * Lets change the camera position
     */
    protected void updateCameraPosition() {
        if (followUnit && isValidPos(cameraFocusUnit.unit().getPosition().toPoint2d())) {
            cameraFocusPosition = cameraFocusUnit.unit().getPosition().toPoint2d();
        }

        // We only do smooth movement, if the focus is nearby.
        double dist = dist(currentCameraPosition, cameraFocusPosition);
        if (dist > cameraJumpThreshold) {
            currentCameraPosition = cameraFocusPosition;
        } else if (dist > 0.1f) {
            currentCameraPosition = currentCameraPosition.add(Point2d.of(
                moveFactor*(cameraFocusPosition.getX() - currentCameraPosition.getX()),
                moveFactor*(cameraFocusPosition.getY() - currentCameraPosition.getY())));
        } else {
            return;
        }
        if (isValidPos(currentCameraPosition)) {
            updateCameraPositionExcecute();
        }
    }
    protected boolean shouldMoveCamera(int priority) {
        long elapsedFrames = m_client.observation().getGameLoop() - lastMoved;
        boolean isTimeToMove = elapsedFrames >= cameraMoveTime;
        boolean isTimeToMoveIfHigherPrio = elapsedFrames >= cameraMoveTimeMin;
        boolean isHigherPrio = lastMovedPriority < priority || (followUnit && !cameraFocusUnit.isAlive());
        // camera should move IF: enough time has passed OR (minimum time has passed AND new prio is higher)
        return isTimeToMove || (isHigherPrio && isTimeToMoveIfHigherPrio);
    }

    /**
     * Check if a player is near the oponent start location
     * @param pos
     * @param player
     * @return
     */
    protected boolean isNearOpponentStartLocation(Point2d pos, int player) {
        return isNearOwnStartLocation(pos, getOpponent(player));
    }

    /**
     * Check if a player is near their own detected start position
     * @param pos
     * @param player
     * @return
     */
    protected boolean isNearOwnStartLocation(Point2d pos, int player) {
        return dist(pos, m_startLocations.get(player)) < nearStartLocationDistance;
    }

    /**
     * Check if a unit should be counted as army or not
     * @param type
     * @return
     */
    protected boolean isArmyUnitType(UnitType type) {
        if (isWorkerType(type)) { return false; }
        if (type == Units.ZERG_OVERLORD) { return false; }  // Excluded here the overlord transport etc to count them as army unit
        if (isBuilding(type)) { return false; }
        if (type == Units.ZERG_EGG) { return false; }
        if (type == Units.ZERG_LARVA) { return false; }
        if (type == Units.PROTOSS_INTERCEPTOR) { return false; }

        return true;
    }

    /**
     * Check if a unit type is a building, We do this by just checking if it has the structure attribute
     * @param type
     * @return
     */
    protected boolean isBuilding(UnitType type) {
        return m_client.observation().getUnitTypeData(false).get(type).getAttributes().contains(UnitAttribute.STRUCTURE);
    }

    /**
     * Check that the point is actually valid
     * @param pos
     * @return
     */
    protected boolean isValidPos(Point2d pos) {
        // Maybe playable width/height?
        var mapSize = m_client.observation().getGameInfo().getStartRaw().get().getMapSize();
        return pos.getX() >= 0 && pos.getY() >= 0 && pos.getX() < mapSize.getX() && pos.getY() < mapSize.getY();
    }

    /**
     * Is a unit under attack
     * @param unit
     * @return
     */
    protected boolean isUnderAttack(Unit unit) {
        return false; //At the moment there is no flag for being under attack
    }

    /**
     * Is a unit attack (or probably attacking)
     * @param attacker
     * @return
     */
    protected boolean isAttacking(Unit attacker) {
        if (!isArmyUnitType(attacker.getType()) || attacker.getDisplayType() != DisplayType.VISIBLE) {
            return false;
        }

        // Option A
        // return unit->orders.size()>0 && unit->orders.front().ability_id.ToType() == sc2::ABILITY_ID::ATTACK_ATTACK;
        // Option B
        // return unit->weapon_cooldown > 0.0f;
        // Option C
        // Not sure if observer can see the "private" fields of player units.
        // So we just assume: if it is in range and an army unit -> it attacks
        var weapons = m_client.observation().getUnitTypeData(false).get(attacker.getType()).getWeapons();
        // TODO: need to fill in data for missing weapon types most likely like BC data

        float rangeAir = -1.0f;
        float rangeGround = -1.0f;
        for (var weapon: weapons) {
            if (weapon.getTargetType() == Weapon.TargetType.ANY) {
                rangeAir = weapon.getRange();
                rangeGround = weapon.getRange();
            } else if (weapon.getTargetType() == Weapon.TargetType.AIR) {
                rangeAir = weapon.getRange();
            } else if (weapon.getTargetType() == Weapon.TargetType.GROUND) {
                rangeGround = weapon.getRange();
            }
        }

        int enemyID = getOpponent(attacker.getOwner());
        for (var unit: m_client.observation().getUnits())
        {
            if (unit.unit().getDisplayType() != DisplayType.VISIBLE || unit.unit().getOwner() != enemyID) {
                continue;
            }

            if (unit.unit().getFlying().orElse(false)) {
                if (dist(attacker, unit.unit()) < rangeAir) {
                    return true;
                }
            } else {
                if (dist(attacker, unit.unit()) < rangeGround) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a unit type is a worker
     * @param type
     * @return
     */
    protected boolean isWorkerType(UnitType type) {
        if (type.equals(Units.TERRAN_SCV) || type.equals(Units.TERRAN_MULE) || type.equals(Units.PROTOSS_PROBE) || type.equals(Units.ZERG_DRONE) || type.equals(Units.ZERG_DRONE_BURROWED)) {
            return true;
        }

        return false;
    }

    /**
     * Get the distance between two units
     * @return
     */
    protected double dist(Unit u1, Unit u2) {
        return dist(u1.getPosition().toPoint2d(), u2.getPosition().toPoint2d());
    }

    /**
     * Get the distance between two points
     * @param p1
     * @param p2
     * @return
     */
    protected double dist(Point2d p1, Point2d p2) {
        return p1.distance(p2);
    }

    /**
     * Find all the town centers and set them as the start locations as we can see everything. This should only be run
     * at the start of the game otherwise it will probably detect incorrectly
     */
    protected void setPlayerStartLocations() {

        m_startLocations.clear();
        var bases = m_client.observation().getUnits(u -> FILTER_TWONCENTER.contains(u.unit().getType()));

        // We are always an observer, so we can see everything we care about
        for(var base: bases) {
            m_startLocations.put(base.unit().getOwner(), base.unit().getPosition().toPoint2d());
        }
    }

    /**
     * Get all the player IDs in this replay and add them to the set of player ids
     */
    protected void setPlayerIds() {

        m_playerIDs.clear();
        for (var player: m_client.observation().getGameInfo().getPlayersInfo()) {
            if (player.getPlayerType().orElse(PlayerType.OBSERVER) != PlayerType.OBSERVER) {
                m_playerIDs.add(player.getPlayerId());
            }
        }
    }

    /**
     * Find the any player that is not the given player id
     * @param player
     * @return
     */
    protected int getOpponent(int player) {
        return m_playerIDs.stream().filter(p -> p != player).findAny().orElse(0);
    }

    /**
     * Invert the given position based on the current map
     * @param pos
     * @return
     */
    protected Point2d getInvertedPos(Point2d pos) {
        var mapSize = m_client.observation().getGameInfo().getStartRaw().get().getMapSize();
        return Point2d.of(mapSize.getX() - pos.getX(), mapSize.getY() - pos.getY());
    }
}
