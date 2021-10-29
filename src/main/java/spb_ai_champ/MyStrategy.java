package spb_ai_champ;

import spb_ai_champ.model.*;
import spb_ai_champ.myClasses.BuildRequest;
import spb_ai_champ.myClasses.MoveEvent;
import spb_ai_champ.myClasses.Pair;

import java.util.*;

public class MyStrategy {

    final Random random = new Random();
    final Map<Resource, Integer> myResources = new HashMap<>();
    final Map<Resource, ArrayList<Integer>> myDevelopedResourceMap = new HashMap<>();
    final Map<Resource, ArrayList<Integer>> myResourceMap = new HashMap<>();
    final Map<Resource, ArrayList<BuildingType>> recyclingBuilding = new HashMap<>();
    final Map<Integer, Integer> attackRobots = new HashMap<>();
    final Set<BuildRequest> buildRequests = new TreeSet<>();
    final int staticDefenders = 0;
    final int firstStageCount = 4;
    int[] workersOnPlanet;
    Map<BuildingType, List<Integer>> myBuildingPlans;
    ArrayList<MoveAction> moveActions;
    ArrayList<BuildingAction> buildActions;
    Set<Integer> myPlanets = new HashSet<>();
    Map<BuildingType, BuildingProperties> quarryProperties;
    Planet[] planets;
    Game game;
    int[][] nearPlanets;
    int[][] planetsGraph;
    int[][] planetsDistance;
    Map<Integer, ArrayList<Integer>> logisticGraph = new HashMap<>();
    Map<Integer, ArrayList<Integer>> reversedLogisticGraph = new HashMap<>();
    int[] sentStone;
    BuildingType[] plannedBuilding;
    int attackers;
    int built;
    int buildPlanned;
    int homePlanet;
    int enemyPlanet;
    int oreLimit;
    int myRobots;
    PriorityQueue<MoveEvent> moveEvents;
    ArrayList<Integer> myResourcePlanets = new ArrayList<>();
    BuildPlansGenerator buildPlansGenerator;

    void start() {
        if (game.getCurrentTick() == 0) {
            built = 0;
            moveEvents = new PriorityQueue<>(1);
            quarryProperties = game.getBuildingProperties();
            sentStone = new int[planets.length];
            //System.out.println(quarryProperties);
            {
                for (Resource resource : Resource.values()) {
                    recyclingBuilding.put(resource, new ArrayList<>());
                }
                for (BuildingType buildingType : BuildingType.values()) {
                    for (Resource resource : quarryProperties.get(buildingType).getWorkResources().keySet()) {
                        recyclingBuilding.get(resource).add(buildingType);
                    }
                }
            } // recyclingBuildings
            {
                for (int planet = 0; planet < planets.length; planet++) {
                    WorkerGroup[] workerGroups = planets[planet].getWorkerGroups();
                    if (workerGroups.length > 0 && workerGroups[0].getPlayerIndex() == game.getMyIndex()) {
                        homePlanet = planet;
                        myPlanets.add(homePlanet);
                    } else if (workerGroups.length > 0) {
                        enemyPlanet = planet;
                    }
                    if (planets[planet].getHarvestableResource() == Resource.ORE) {
                        oreLimit++;
                    }
                }
                oreLimit = 1;
            } // find color, homePlanet and medianLine
            {
                planetsDistance = new int[planets.length][planets.length];
                planetsGraph = new int[planets.length][planets.length];
                for (int planet = 0; planet < planets.length; planet++) {
                    for (int nextPlanet = 0; nextPlanet < planets.length; nextPlanet++) {
                        planetsDistance[planet][nextPlanet] = 1000;
                        planetsGraph[planet][nextPlanet] = -1;
                        int distance = Math.abs(planets[planet].getX() - planets[nextPlanet].getX()) +
                                Math.abs(planets[planet].getY() - planets[nextPlanet].getY());
                        if (distance <= game.getMaxTravelDistance()) {
                            planetsDistance[planet][nextPlanet] = distance;
                            planetsGraph[planet][nextPlanet] = distance;
                        }
                    }
                }
                for (int planetMid = 0; planetMid < planets.length; planetMid++) {
                    for (int planet = 0; planet < planets.length; planet++) {
                        for (int planetNext = 0; planetNext < planets.length; planetNext++) {
                            int distance = planetsDistance[planet][planetMid] + planetsDistance[planetMid][planetNext];
                            planetsDistance[planet][planetNext] = Math.min(planetsDistance[planet][planetNext], distance);
                        }
                    }
                }
            } // planetsDistance and planetsGraph
            {
                {
                    for (Resource resource : Resource.values()) {
                        myResourceMap.put(resource, new ArrayList<>());
                        myDevelopedResourceMap.put(resource, new ArrayList<>());
                        myResources.put(resource, 0);
                    }
                } // put res in map
                for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
                    Planet planet = planets[planetIndex];
                    if (planet.getHarvestableResource() != null) {
                        myResourceMap.get(planet.getHarvestableResource()).add(planetIndex);
                    }
                }

            }  //myResourceMap
            {
                nearPlanets = new int[planets.length][];
                for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
                    ArrayList<Integer> arrayList = new ArrayList<>();
                    for (int i = 0; i < planets.length; i++) {
                        arrayList.add(i);
                    }
                    int finalPlanetIndex = planetIndex;
                    Comparator<Integer> planetDistanceComp = Comparator.comparingInt(o -> planetsDistance[o][finalPlanetIndex]);
                    arrayList.sort(planetDistanceComp);
                    int[] arr = new int[planets.length];
                    for (int i = 0; i < planets.length; i++) {
                        arr[i] = arrayList.get(i);
                    }
                    nearPlanets[planetIndex] = arr;
                }
            } //near planets
        } else if (game.getCurrentTick() == 1) {
            {
//                myBuildingPlans.remove(BuildingType.QUARRY);
                buildPlansGenerator = new BuildPlansGenerator();

                //System.out.println(myBuildingPlans);
            } //fill myBuildingPlans
            System.out.println("end 1");
        } else if (game.getCurrentTick() == 2) {
            System.out.println("start 2");
            {
                myBuildingPlans = new HashMap<>(buildPlansGenerator.genBySimulatedAnnealing());
                for (BuildingType buildingType : BuildingType.values()) {
                    myBuildingPlans.putIfAbsent(buildingType, new ArrayList<>());
                }
                myBuildingPlans.get(BuildingType.QUARRY).add(homePlanet);
                plannedBuilding = new BuildingType[planets.length];
                for (BuildingType buildingType : BuildingType.values()) {
                    for (int planetIndex : myBuildingPlans.get(buildingType)) {
                        plannedBuilding[planetIndex] = buildingType;
                        buildPlanned++;
                    }
                }
            }
            {
                for (BuildingType buildingType : BuildingType.values()) {
                    for (int planetIndex : myBuildingPlans.get(buildingType)) {
                        buildRequests.add(new BuildRequest(game.getCurrentTick(), planetIndex, buildingType, quarryProperties));
                        System.out.println(planetIndex + " " + buildingType);
                    }
                }
            }
            {
                for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
                    for (int myPlanetIndex = 0; myPlanetIndex < planets.length; myPlanetIndex++) {
                        if (planetsGraph[planetIndex][myPlanetIndex] != -1 && plannedBuilding[myPlanetIndex] != null) {
                            myPlanets.add(planetIndex);
                        }
                    }
                }
            } // my planets
            {
                for (int i = 1; i < 4; i++) {
                    myResourcePlanets.addAll(myBuildingPlans.get(BuildingType.values()[i]));
                }
            } //myResourcePlanets
            {
                for (int i = 0; i < planets.length; i++) {
                    logisticGraph.put(i, new ArrayList<>());
                    reversedLogisticGraph.put(i, new ArrayList<>());
                }
                for (BuildingType sender : BuildingType.values()) {
                    if (quarryProperties.get(sender).getProduceResource() == null) continue;
                    for (BuildingType recycler : recyclingBuilding.get(quarryProperties.get(sender).getProduceResource())) {
                        for (int senderIndex : myBuildingPlans.get(sender)) {
                            logisticGraph.putIfAbsent(senderIndex, new ArrayList<>());
                            for (int recyclerIndex : myBuildingPlans.get(recycler)) {
                                logisticGraph.get(senderIndex).add(recyclerIndex);
                            }
                        }
                        for (int recyclerIndex : myBuildingPlans.get(recycler)) {
                            reversedLogisticGraph.putIfAbsent(recyclerIndex, new ArrayList<>());
                            for (int senderIndex : myBuildingPlans.get(sender)) {
                                reversedLogisticGraph.get(recyclerIndex).add(senderIndex);
                            }
                        }
                    }
                }
            } //Logistic graph
            System.out.println("end 2");
        }
        System.out.println(myBuildingPlans);
    }

    void sendRobotsToMyPlanets() {
        for (int planetIndex : myPlanets) {
            moveActions.add(new MoveAction(homePlanet, planetIndex, staticDefenders, null));
        }
    }

    void getEvents(Map<Integer, ArrayList<MoveEvent>> eventsMap) {
        while (moveEvents.peek() != null && moveEvents.peek().getTick() == game.getCurrentTick()) {
            MoveEvent moveEvent = moveEvents.poll();
            assert moveEvent != null;
            eventsMap.putIfAbsent(moveEvent.getPlanet(), new ArrayList<>());
            eventsMap.get(moveEvent.getPlanet()).add(moveEvent);
        }
    }

    void setEvent(MoveAction moveAction, ArrayList<MoveAction> newMoveActions) {
        if (countEnemyRobots(moveAction.getStartPlanet()) != 0) {
            moveEvents.add(new MoveEvent(game.getCurrentTick() + 1,
                    moveAction.getStartPlanet(), moveAction.getTargetPlanet(), moveAction.getTakeResource(),
                    moveAction.getWorkerNumber()));
        }
        if (planetsGraph[moveAction.getStartPlanet()][moveAction.getTargetPlanet()] == -1) {
            int nextPlanet = findNextPlanet(moveAction.getStartPlanet(), moveAction.getTargetPlanet());
            moveEvents.add(new MoveEvent(game.getCurrentTick() + planetsDistance[moveAction.getStartPlanet()][nextPlanet],
                    nextPlanet, moveAction.getTargetPlanet(), moveAction.getTakeResource(),
                    moveAction.getWorkerNumber()));
            newMoveActions.add(new MoveAction(moveAction.getStartPlanet(), nextPlanet, moveAction.getWorkerNumber(),
                    moveAction.getTakeResource()));
        } else {
            newMoveActions.add(moveAction);
        }
    }

    int findNextPlanet(int planet, int endPlanet) {
        int minDistance = 100000;
        int nextPlanet = 0;
        for (int i = 1; i < planets.length; i++) {
            int thisPlanet = nearPlanets[planet][i];
            if (planetsGraph[planet][thisPlanet] == -1) {
                break;
            }
            int path = planetsDistance[planet][thisPlanet] + planetsDistance[thisPlanet][endPlanet];
            if (path < minDistance) {
                nextPlanet = thisPlanet;
                minDistance = path;
            }
        }
        return nextPlanet;
    }


    public Action getAction(Game game) {
        this.game = game;
        planets = game.getPlanets();
        moveActions = new ArrayList<>();
        buildActions = new ArrayList<>();
        for (Resource resource : Resource.values()) {
            myResources.put(resource, 0);
        }
        countMyRobots();
        if (game.getCurrentTick() == 0) {
            start();
            return new Action(new MoveAction[0], new BuildingAction[0], Specialty.LOGISTICS);
        }
        if (game.getCurrentTick() == 1) {
            start();
            return new Action(new MoveAction[0], new BuildingAction[0], null);
        } else if (game.getCurrentTick() == 2) {
            start();
            sendRobotsToMyPlanets();
        }
        workersOnPlanet = new int[planets.length];
        Map<Integer, ArrayList<MoveEvent>> eventsMap = new TreeMap<>();
        getEvents(eventsMap);
        for (BuildingType buildingType : BuildingType.values()) {
            for (int planetIndex : myBuildingPlans.get(buildingType)) {
                if (planets[planetIndex].getResources().getOrDefault(Resource.STONE, 0) >=
                        quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE) &&
                        planets[planetIndex].getBuilding() == null) {
                    buildActions.add(new BuildingAction(planetIndex, buildingType));
                }
                if (planets[planetIndex].getBuilding() == null) {
                    BuildRequest buildRequest = new BuildRequest(game.getCurrentTick(), planetIndex, buildingType, quarryProperties);
                    buildRequests.add(buildRequest);
                }
            }
        }
        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            int myWorkers = countRobotsOnPlanet(planetIndex);
            myWorkers -= myPlanets.contains(planetIndex) ? staticDefenders : 0;
            if (myWorkers <= 0) continue;
            for (MoveEvent moveEvent : eventsMap.getOrDefault(planetIndex, new ArrayList<>())) {
                int robots = Math.min(moveEvent.getRobots(), myWorkers);
                if (moveEvent.getResource() != null) {
                    robots = Math.min(robots, planets[planetIndex].getResources().getOrDefault(moveEvent.getResource(),
                            0));
                }
                moveActions.add(new MoveAction(planetIndex, moveEvent.getEndPlanet(), robots, moveEvent.getResource()));
                myWorkers -= robots;
            }
            if (plannedBuilding[planetIndex] != null && plannedBuilding[planetIndex].tag <= 4) {
                myWorkers = Math.max(0, myWorkers - 10);
            }
            workersOnPlanet[planetIndex] = myWorkers;
        }
        System.out.println(workersOnPlanet[homePlanet]);
        {
            List<BuildRequest> toDelete = new ArrayList<>();
            for (BuildRequest buildRequest : buildRequests) {
                if (planets[buildRequest.getPlanetIndex()].getBuilding() != null || game.getCurrentTick() > buildRequest.getTickStarted() + 100) {
                    toDelete.add(buildRequest);
                }
                if (game.getCurrentTick() == buildRequest.getTickStarted() + 50 && buildRequest.getSentRobotsFromStone() < buildRequest.getSentRobotsToStone()) {
                    System.out.println("Hello");
                    buildRequest.setSentRobotsToStone(buildRequest.getSentRobotsFromStone());
                }
            }
            for (BuildRequest buildRequest : toDelete) {
                buildRequests.remove(buildRequest);
            }
            if (buildRequests.size() != 0) System.out.println(buildRequests);
        } // clearing buildRequests
        {
            Map<Integer, Pair<Integer, Integer>> threats = findEnemyAttacker();
            HashMap<Integer, Integer> futureThreats = new HashMap<>();
            if (threats.size() != 0) System.out.println(threats);
            for (Map.Entry<Integer, Pair<Integer, Integer>> threat : threats.entrySet()) {
                int threatPlanet = threat.getKey();
                int threatTick = threat.getValue().getFirst();
                int threatNumber = threat.getValue().getSecond();
                for (int planetIndexFind = 0; planetIndexFind < planets.length; planetIndexFind++) {
                    int planetIndex = nearPlanets[threatPlanet][planetIndexFind];
                    int support = Math.min(threatNumber, workersOnPlanet[planetIndex]);
                    if (planetsDistance[planetIndex][threatPlanet] > threatTick - game.getCurrentTick()) {
                        moveActions.add(new MoveAction(planetIndex, threatPlanet, support / 2, null));
                        workersOnPlanet[planetIndex] -= support / 2;
                        futureThreats.put(threatPlanet, threatNumber / 2);
                    } else {
                        moveActions.add(new MoveAction(planetIndex, threatPlanet, support, null));
                        workersOnPlanet[planetIndex] -= support;
                    }
                    threatNumber -= support;
                }
            }
        } // defend me
        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            if (planetIndex == homePlanet) {
                int homePlanetStone = planets[homePlanet].getResources().getOrDefault(Resource.STONE, 0);
                for (BuildRequest buildRequest : buildRequests) {
                    int needSend = Math.min(workersOnPlanet[planetIndex], buildRequest.needSendFromStone());
                    if (needSend <= 0) {
                        continue;
                    }
                    if (homePlanetStone < needSend) {
                        workersOnPlanet[planetIndex] -= Math.min(quarryProperties.get(BuildingType.QUARRY).getMaxWorkers(),
                                workersOnPlanet[planetIndex]);
                        break;
                    }
                    System.out.println(needSend);
                    moveActions.add(new MoveAction(planetIndex, buildRequest.getPlanetIndex(), needSend, Resource.STONE));
                    homePlanetStone -= needSend;
                    buildRequest.setSentRobotsFromStone(buildRequest.getSentRobotsFromStone() + needSend);
                    workersOnPlanet[planetIndex] -= needSend;
                }
                if (buildRequests.size() == 0) {
                    shipment(planetIndex, Math.max(workersOnPlanet[planetIndex], 0));
                }
            }
            for (BuildRequest buildRequest : buildRequests) {
                int needSend = Math.min(workersOnPlanet[planetIndex], buildRequest.needSendToStone());
                if (needSend <= 0) {
                    continue;
                }
                if (planetIndex != homePlanet) {
                    moveActions.add(new MoveAction(planetIndex, homePlanet, needSend, null));
                }
                buildRequest.setSentRobotsToStone(buildRequest.getSentRobotsToStone() + needSend);
                workersOnPlanet[planetIndex] -= needSend;
            }
        }
        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            int myWorkers = countRobotsOnPlanet(planetIndex);
            if (myWorkers > 0) {
                for (Resource resource : Resource.values()) {
                    myResources.put(resource, myResources.get(resource) + planets[planetIndex].getResources().getOrDefault(resource, 0));
                }
            }
        }

        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            int myWorkers = workersOnPlanet[planetIndex];
            int attack = attackRobots.getOrDefault(planetIndex, 0);
            if (plannedBuilding[planetIndex] == BuildingType.REPLICATOR &&
                    findPlanetToAttack(planetIndex) != myBuildingPlans.get(BuildingType.REPLICATOR).get(0)) {
                int toAttack = Math.min(myWorkers * 9 / 10, Math.max(myRobots - attackers - 1100, 0));
                myWorkers -= toAttack;
                attack += toAttack;
                attackRobots.put(planetIndex, attack);
//                System.out.println(attackRobots + " " + myWorkers + " " + attack + " " + myRobots + " " + attackers);
                if (attack > 100) {
                    Attack(planetIndex, attack);
                }
            } else if (!myPlanets.contains(planetIndex) && planets[planetIndex].getBuilding() != null) {
                buildActions.add(new BuildingAction(planetIndex, null));
                myWorkers = 0;
            } else if (!myPlanets.contains(planetIndex) && buildRequests.size() == 0) {
                attack = workersOnPlanet[planetIndex];
                Attack(planetIndex, attack);
                myWorkers = 0;
            }


            if (myWorkers <= 0) continue;
            int cachedWorkers = myWorkers;
            if (planets[planetIndex].getBuilding() != null && plannedBuilding[planetIndex] != null &&
                    planetIndex != homePlanet && myPlanets.contains(planetIndex)) {
                BuildingType buildingType = plannedBuilding[planetIndex];
                Resource planetResource = quarryProperties.get(buildingType).getProduceResource();
                for (int loop = 0; loop < 3; loop++) {
                    Map<Pair<Integer, Integer>, Float> calcValue = new HashMap<>();
                    for (int divisorConstant = 1; divisorConstant < 6; divisorConstant++) {
                        myWorkers = cachedWorkers;

                        if (buildingType.tag >= 4) {
                            int toWork = productionForRes(planets[planetIndex].getResources(),
                                    quarryProperties.get(buildingType).getWorkResources());
                            myWorkers -= Math.min(toWork * quarryProperties.get(buildingType).getProduceAmount(),
                                    quarryProperties.get(buildingType).getMaxWorkers());
                        } else {
                            if (planets[planetIndex].getResources().getOrDefault(quarryProperties.get(buildingType).getProduceResource(), 0) < myWorkers / divisorConstant) {
                                myWorkers -= quarryProperties.get(buildingType).getMaxWorkers();
                            }
                        }
                        if (myWorkers / divisorConstant <= 0) {
                            continue;
                        }

                        for (int endPlanetIndex : logisticGraph.get(planetIndex)) {
                            if (planets[planetIndex].getResources().getOrDefault(planetResource, 0) > myWorkers / divisorConstant) {
                                calcValue.put(new Pair<>(endPlanetIndex, divisorConstant),
                                        calcShipmentValue(planetIndex, endPlanetIndex, myWorkers / divisorConstant, false)
                                );
                            }
                        }
                        for (int endPlanetIndex : reversedLogisticGraph.get(planetIndex)) {
                            calcValue.put(new Pair<>(endPlanetIndex, divisorConstant),
                                    calcShipmentValue(planetIndex, endPlanetIndex, myWorkers / divisorConstant, true)
                            );
                        }
                    }
                    myWorkers = cachedWorkers;
                    float normalizeConst = 10000000f;
                    for (Map.Entry<Pair<Integer, Integer>, Float> entry : calcValue.entrySet()) {
                        Float v = entry.getValue();
                        if (v < normalizeConst) {
                            normalizeConst = v;
                        }
                    }
                    normalizeConst = Math.max(-normalizeConst, 0);
                    for (Map.Entry<Pair<Integer, Integer>, Float> entry : calcValue.entrySet()) {
                        calcValue.put(entry.getKey(), entry.getValue() + normalizeConst);
                    }
                    float maxValue = -100000f;
                    Pair<Integer, Integer> maxPlanetIndex = new Pair<>(homePlanet, 1000);
                    for (Map.Entry<Pair<Integer, Integer>, Float> entry : calcValue.entrySet()) {
                        Pair<Integer, Integer> k = entry.getKey();
                        Float v = entry.getValue();
                        float value = v * Math.max(0.9f, random.nextFloat());
                        if (value > maxValue) {
                            maxPlanetIndex = k;
                            maxValue = value;
                        }
                    }
                    //System.out.println(calcValue + " " + maxPlanetIndex + " " + maxValue + " " + normalizeConst);
                    int endPlanetIndex = maxPlanetIndex.getFirst();
                    int divisorConstant = maxPlanetIndex.getSecond();
                    Resource resToSend;
                    if (logisticGraph.get(planetIndex).contains(endPlanetIndex)) {
                        resToSend = quarryProperties.get(buildingType).getProduceResource();
                    } else {
                        resToSend = null;
                    }
                    if (buildingType.tag >= 4) {
                        int toWork = productionForRes(planets[planetIndex].getResources(),
                                quarryProperties.get(buildingType).getWorkResources());
                        myWorkers -= Math.min(toWork * quarryProperties.get(buildingType).getProduceAmount(),
                                quarryProperties.get(buildingType).getMaxWorkers());
                    } else {
                        if (planets[planetIndex].getResources().getOrDefault(quarryProperties.get(buildingType).getProduceResource(), 0) < myWorkers / divisorConstant) {
                            myWorkers -= quarryProperties.get(buildingType).getMaxWorkers();
                        }
                    }
                    if (myWorkers / divisorConstant <= 0) {
                        continue;
                    }
                    int workers = myWorkers / divisorConstant;
                    myWorkers -= workers;
                    if (findPlanetToAttack(0) != myBuildingPlans.get(BuildingType.REPLICATOR).get(0)) {
                        int defenders = myWorkers / 10;
                        myWorkers -= defenders;
                        findPlanetToSendRobots(planetIndex, defenders);
                    }
                    cachedWorkers = myWorkers;
                    moveActions.add(new MoveAction(planetIndex, endPlanetIndex, workers, resToSend));
//                    if (buildingType == BuildingType.REPLICATOR) {
//                        System.out.println(moveActions.get(moveActions.size() - 1) + " " + game.getCurrentTick());
//                    }
                }
            } else {
                if (myWorkers > 1 && planetIndex != homePlanet) {
                    if (myPlanets.contains(planetIndex)) {
                        int tmp = myWorkers / 2;
                        myWorkers -= tmp;
                        myWorkers += findPlanetToSendRobots(planetIndex, tmp);
                        shipment(planetIndex, myWorkers);
                    } else {
                        findPlanetToSendRobots(planetIndex, myWorkers);
                    }
                }
            }
            workersOnPlanet[planetIndex] = myWorkers;
        }
        checkFlyingGroups();
        MoveAction[] moveActionsArray = new MoveAction[moveActions.size()];
        BuildingAction[] buildingActionsArray = new BuildingAction[buildActions.size()];
        return new Action(moveActions.toArray(moveActionsArray), buildActions.toArray(buildingActionsArray), null);
    }

    private void Attack(int planetIndex, int attack) {
        int attackPlanet = findPlanetToAttack(planetIndex);
        if (attackPlanet == -1) {
            moveActions.add(new MoveAction(planetIndex, myBuildingPlans.get(BuildingType.REPLICATOR).get(0), attack, null));
        } else {
            int nextPlanet = findNextPlanet(planetIndex, attackPlanet);
            moveActions.add(new MoveAction(planetIndex, attackPlanet,
                    attack, null));
            attackRobots.put(nextPlanet, attack);
        }
        attackRobots.put(planetIndex, 0);
    }

    int findPlanetToSendRobots(int planetIndex, int robots) {
        for (int myPlanetIndex : myPlanets) {
            int lack = staticDefenders - countRobotsOnPlanet(myPlanetIndex);
            if (lack > 0) {
                int sendCount = Math.min(robots, lack);
                moveActions.add(new MoveAction(planetIndex, myPlanetIndex, sendCount, null));
                robots -= sendCount;
            }
        }
        return robots;
    }

    int findPlanetToAttack(int planetIndex) {
        for (int i = 0; i < planets.length; i++) {
            int thisPlanet = nearPlanets[planetIndex][i];
            if (countEnemyRobots(thisPlanet) != 0) {
                return thisPlanet;
            }
        }
        return -1;
    }

    int countEnemyRobots(int planetIndex) {
        int ans = 0;
        for (WorkerGroup workerGroup : planets[planetIndex].getWorkerGroups()) {
            if (isEnemyIndex(workerGroup.getPlayerIndex())) {
                ans += workerGroup.getNumber();
            }
        }
        return ans;
    }

    void countMyRobots() {
        attackers = 0;
        for (Map.Entry<Integer, Integer> p : attackRobots.entrySet()) {
            attackers += p.getValue();
        }
        myRobots = 0;
        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            myRobots += countRobotsOnPlanet(planetIndex);
        }
        for (FlyingWorkerGroup flyingWorkerGroup : game.getFlyingWorkerGroups()) {
            myRobots += flyingWorkerGroup.getPlayerIndex() == game.getMyIndex() ? flyingWorkerGroup.getNumber() : 0;
        }
    }

    void checkFlyingGroups() {
        ArrayList<MoveAction> newMoveActions = new ArrayList<>();
        moveActions.sort(Comparator.comparingInt(MoveAction::getWorkerNumber));
        Collections.reverse(moveActions);
        int flyingGroups = 0;
        for (FlyingWorkerGroup flyingWorkerGroup : game.getFlyingWorkerGroups()) {
            flyingGroups += flyingWorkerGroup.getPlayerIndex() == game.getMyIndex() ? 1 : 0;
        }
        int i = 0;
        while (i < moveActions.size() && flyingGroups + i < game.getMaxFlyingWorkerGroups()) {
            MoveAction moveAction = moveActions.get(i);
            if (moveAction.getWorkerNumber() <= 0) break;

            i++;
            if (moveAction.getStartPlanet() == moveAction.getTargetPlanet()) continue;
            setEvent(moveAction, newMoveActions);
        }
        while (i < moveActions.size()) {
            MoveAction moveAction = moveActions.get(i);
            if (moveAction.getWorkerNumber() <= 0) break;
            i++;
            if (moveAction.getStartPlanet() == moveAction.getTargetPlanet()) continue;
            moveEvents.add(new MoveEvent(game.getCurrentTick() + 1,
                    moveAction.getStartPlanet(), moveAction.getTargetPlanet(), moveAction.getTakeResource(),
                    moveAction.getWorkerNumber()));
        }
        moveActions = newMoveActions;
    }

    void shipment(int planetIndex, int myWorkers) {
        if (myWorkers / myResourcePlanets.size() <= 0) return;
        for (int indexPlanet : myResourcePlanets) {
            moveActions.add(new MoveAction(planetIndex, indexPlanet, myWorkers / myResourcePlanets.size(), null));
        }

    }

    int productionForRes(Map<Resource, Integer> myResources, Map<Resource, Integer> resourceNeed) {
        int[] ans = new int[]{Integer.MAX_VALUE};
        resourceNeed.forEach((k, v) -> ans[0] = Math.min(ans[0], myResources.getOrDefault(k, 0) / v));
        return ans[0];
    }

    int countRobotsOnPlanet(int planetIndex) {
        int robots = 0;
        for (WorkerGroup workerGroup : planets[planetIndex].getWorkerGroups()) {
            robots += workerGroup.getPlayerIndex() == game.getMyIndex() ? workerGroup.getNumber() : 0;
        }
        return robots;
    }

    float calcShipmentValue(int startPlanetIndex, int endPlanetIndex, int robots, boolean reversed) {
        float value = 0;
        BuildingType startPlanetBuilding = plannedBuilding[startPlanetIndex];
        BuildingType endPlanetBuilding = plannedBuilding[endPlanetIndex];

        if (!reversed) {
            Resource resToSend = quarryProperties.get(startPlanetBuilding).getProduceResource();
            value += (1f / 15f) * (calcShipmentScore(endPlanetIndex, resToSend, robots) -
                    calcShipmentScore(endPlanetIndex, resToSend, 0));
            //System.out.print(value + " ");

            value += (1f / 1.1f) * planets[startPlanetIndex].getResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");
            value += -(1f / 100f) * planets[endPlanetIndex].getResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");

            if (endPlanetBuilding.tag < 9) {
                value -= (1f / 100f) * Math.max(myResources.getOrDefault(quarryProperties.get(endPlanetBuilding).getProduceResource(), 0) - 100, 0);
            }

            if (endPlanetBuilding == BuildingType.ACCUMULATOR_FACTORY) {
                value = value > 0 ? value / 1.2f : value * 1.2f;
            }

            //System.out.print(value + " ");
        } else {
            Resource resToSend = quarryProperties.get(endPlanetBuilding).getProduceResource();
            value += -(1f / 2f) * Math.max(-1, (countRobotsOnPlanet(endPlanetIndex) -
                    quarryProperties.get(plannedBuilding[endPlanetIndex]).getMaxWorkers()));
            //System.out.print(value + " ");
            value += (1f / 20f) * (calcShipmentScore(startPlanetIndex, resToSend, robots) -
                    calcShipmentScore(startPlanetIndex, resToSend, 0));
            //System.out.print(value + " ");
            value -= (1f / 60f) * planets[startPlanetIndex].getResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");
            value += (1f / 1.1f) *
                    quarryProperties.get(startPlanetBuilding).getWorkResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");

//            if (planets[endPlanetIndex].getResources().getOrDefault(resToSend, 0) > 50) {
//                value -= (2f / 1f) * countRobotsOnPlanet(endPlanetIndex);
//            }
        }

        return value;
    }

    int getBuildingLimit(BuildingType buildingType) {
        return switch (buildingType) {
            case FOUNDRY -> 2;
            case QUARRY, REPLICATOR2, REPLICATOR3 -> 0;
            default -> 1;
        };
    }

    Map<Integer, Pair<Integer, Integer>> findEnemyAttacker() {
        Map<Integer, Pair<Integer, Integer>> ans = new HashMap<>();
        for (FlyingWorkerGroup flyingWorkerGroup : game.getFlyingWorkerGroups()) {
            if (isEnemyIndex(flyingWorkerGroup.getPlayerIndex()) && myPlanets.contains(flyingWorkerGroup.getTargetPlanet())) {
                int tick = planetsDistance[flyingWorkerGroup.getDeparturePlanet()][flyingWorkerGroup.getTargetPlanet()] + flyingWorkerGroup.getDepartureTick();
                ans.put(flyingWorkerGroup.getTargetPlanet(), new Pair<>(tick, flyingWorkerGroup.getNumber()));
            }
        }
        for (int planetIndex : myPlanets) {
            int enemyRobots = countEnemyRobots(planetIndex);
            if (enemyRobots != 0) {
                ans.put(planetIndex, new Pair<>(game.getCurrentTick(), enemyRobots));
            }
        }
        return ans;
    }

    boolean isEnemyIndex(int playerIndex) {
        if (game.getPlayers().length == 2) {
            return playerIndex == 1;
        } else {
            return playerIndex >= 3;
        }
    }

    float calcShipmentScore(int planetIndex, Resource resource, int robots) {
        Map<Resource, Integer> planetResources = new TreeMap<>(planets[planetIndex].getResources());
        planetResources.put(resource, planetResources.getOrDefault(resource, 0) + robots);
        Building building = planets[planetIndex].getBuilding();
        if (building == null) return 0f;
        BuildingType buildingType = building.getBuildingType();
        return productionForRes(planetResources,
                quarryProperties.get(buildingType).getWorkResources()) * quarryProperties.get(buildingType).getProduceScore();
    }

    class BuildPlansGenerator {

        Map<BuildingType, List<Integer>> condition;
        Map<BuildingType, List<Integer>> newCondition = new TreeMap<>();
        Map<BuildingType, List<Integer>> bestCondition = new TreeMap<>();
        float bestValueOfCondition = 100000000000000000000000000f;

        BuildPlansGenerator() {
            genFirstCondition();
        }

        int getResToOneRobot(BuildingType sender, BuildingType recycler) {
            return switch (sender) {
                case MINES -> 16;
                case CAREER -> 8;
                case FARM -> 4;
                case FOUNDRY -> switch (recycler) {
                    case CHIP_FACTORY -> 4;
                    case ACCUMULATOR_FACTORY -> 2;
                    case REPLICATOR -> 2;
                    default -> 0;
                };
                case FURNACE -> 4;
                case BIOREACTOR -> 2;
                case CHIP_FACTORY -> 2;
                case ACCUMULATOR_FACTORY -> 1;
                default -> 0;
            };
        }

        float estimateCondition(Map<BuildingType, List<Integer>> thisCondition) {
            float value = 0;
            for (BuildingType sender : BuildingType.values()) {
                Resource sendRes = quarryProperties.get(sender).getProduceResource();
                if (sendRes != null) {
                    for (int senderIndex : thisCondition.get(sender)) {
                        for (BuildingType recycler : recyclingBuilding.get(sendRes)) {
                            for (int recyclerIndex : thisCondition.get(recycler)) {
//                                System.out.println(sender + " " + recycler);
                                value += (3f) * (1f / thisCondition.get(sender).size()) *
                                        (float) planetsDistance[senderIndex][recyclerIndex] *
                                        getResToOneRobot(sender, recycler);
                            }
                        }
                    }
                }
            }
//            System.out.print(thisCondition + " " + value + " ");
            for (BuildingType sender : BuildingType.values()) {
                for (int senderIndex : thisCondition.get(sender)) {
                    value += (8f / 2f) * (planetsDistance[homePlanet][senderIndex]);
                }
            }
//            System.out.println(value);
            return value;
        }
        void genFirstCondition() {
            boolean[] used = new boolean[planets.length];
            used[homePlanet] = true;
            condition = new TreeMap<>();
            for (BuildingType buildingType : BuildingType.values()) {
                condition.put(buildingType, new ArrayList<>());
            }
            int cntOrganicPlanet = 0;
            for (int organicPlanetFind = 1; organicPlanetFind < planets.length; organicPlanetFind++) {
                if (planets[nearPlanets[homePlanet][organicPlanetFind]].getHarvestableResource() != Resource.ORGANICS) {
                    continue;
                }
                cntOrganicPlanet++;
                if (cntOrganicPlanet > myResourceMap.get(Resource.ORGANICS).size() / 2) {
                    break;
                }
                int organicPlanet = nearPlanets[homePlanet][organicPlanetFind];
                used[organicPlanet] = true;
                int cntSandPlanet = 0;
                for (int sandPlanetFind = 1; sandPlanetFind < planets.length; sandPlanetFind++) {
                    if (planets[nearPlanets[organicPlanet][sandPlanetFind]].getHarvestableResource() != Resource.SAND) {
                        continue;
                    }
                    cntSandPlanet++;
                    if (cntSandPlanet > 3) {
                        break;
                    }
                    int cntOrePlanet = 0;
                    int sandPlanet = nearPlanets[organicPlanet][sandPlanetFind];
                    used[sandPlanet] = true;
                    for (int orePlanetFind = 1; orePlanetFind < planets.length; orePlanetFind++) {
                        if (planets[nearPlanets[organicPlanet][orePlanetFind]].getHarvestableResource() != Resource.ORE) {
                            continue;
                        }
                        cntOrePlanet++;
                        if (cntOrePlanet > 3) {
                            break;
                        }
                        int orePlanet = nearPlanets[organicPlanet][orePlanetFind];
                        used[orePlanet] = true;
                        int cntPlasticPlanet = 0;
                        for (int plasticPlanetFind = 1; plasticPlanetFind < planets.length; plasticPlanetFind++) {
                            if (used[nearPlanets[organicPlanet][plasticPlanetFind]]) {
                                continue;
                            }
                            cntPlasticPlanet++;
                            if (cntPlasticPlanet > 3) {
                                break;
                            }
                            int plasticPlanet = nearPlanets[organicPlanet][plasticPlanetFind];
                            used[plasticPlanet] = true;
                            int cntFurnacePlanet = 0;
                            for (int furnacePlanetFind = 1; furnacePlanetFind < planets.length; furnacePlanetFind++) {
                                if (used[nearPlanets[sandPlanet][furnacePlanetFind]]) {
                                    continue;
                                }
                                cntFurnacePlanet++;
                                if (cntFurnacePlanet > 3) {
                                    break;
                                }
                                int furnacePlanet = nearPlanets[sandPlanet][furnacePlanetFind];
                                int firstFoundryPlanet = 0, secondFoundryPlanet = 0;
                                used[furnacePlanet] = true;
                                int cntFoundryPlanet = 0;
                                for (int foundryPlanetFind = 1; foundryPlanetFind < planets.length; foundryPlanetFind++) {
                                    if (used[nearPlanets[orePlanet][foundryPlanetFind]]) {
                                        continue;
                                    }
                                    cntFoundryPlanet++;
                                    if (cntFoundryPlanet >= 3) {
                                        break;
                                    }
                                    int foundryPlanet = nearPlanets[orePlanet][foundryPlanetFind];
                                    used[foundryPlanet] = true;
                                    if (cntFoundryPlanet == 1) firstFoundryPlanet = foundryPlanet;
                                    else secondFoundryPlanet = foundryPlanet;
                                }
                                int chtChipPlanet = 0;
                                for (int chipPlanetFind = 1; chipPlanetFind < planets.length; chipPlanetFind++) {
                                    if (used[nearPlanets[furnacePlanet][chipPlanetFind]]) {
                                        continue;
                                    }
                                    chtChipPlanet++;
                                    if (chtChipPlanet > 6) {
                                        break;
                                    }
                                    int chipPlanet = nearPlanets[furnacePlanet][chipPlanetFind];
                                    used[chipPlanet] = true;
                                    int cntAccumulatorPlanet = 0;
                                    for (int accumulatorPlanetFind = 1; accumulatorPlanetFind < planets.length; accumulatorPlanetFind++) {
                                        if (used[nearPlanets[plasticPlanet][accumulatorPlanetFind]]) {
                                            continue;
                                        }
                                        cntAccumulatorPlanet++;
                                        if (cntAccumulatorPlanet > 5) {
                                            break;
                                        }
                                        int accumulatorPlanet = nearPlanets[plasticPlanet][accumulatorPlanetFind];
                                        used[accumulatorPlanet] = true;
                                        int cntReplicatorPlanet = 0;
                                        for (int replicatorPlanetFind = 1; replicatorPlanetFind < planets.length; replicatorPlanetFind++) {
                                            if (used[nearPlanets[orePlanet][replicatorPlanetFind]]) {
                                                continue;
                                            }
                                            cntReplicatorPlanet++;
                                            if (cntReplicatorPlanet > 12) {
                                                break;
                                            }
                                            int replicatorPlanet = nearPlanets[orePlanet][replicatorPlanetFind];
                                            used[replicatorPlanet] = true;
                                            int use = 0;
                                            for (boolean t : used) use += t ? 1 : 0;
                                            if (use != 11) continue;
                                            condition = new TreeMap<>();
                                            for (BuildingType buildingType : BuildingType.values()) {
                                                condition.put(buildingType, new ArrayList<>());
                                            }
                                            condition.get(BuildingType.MINES).add(orePlanet);
                                            condition.get(BuildingType.CAREER).add(sandPlanet);
                                            condition.get(BuildingType.FARM).add(organicPlanet);
                                            condition.get(BuildingType.FOUNDRY).add(firstFoundryPlanet);
                                            condition.get(BuildingType.FOUNDRY).add(secondFoundryPlanet);
                                            condition.get(BuildingType.FURNACE).add(furnacePlanet);
                                            condition.get(BuildingType.BIOREACTOR).add(plasticPlanet);
                                            condition.get(BuildingType.CHIP_FACTORY).add(chipPlanet);
                                            condition.get(BuildingType.ACCUMULATOR_FACTORY).add(accumulatorPlanet);
                                            condition.get(BuildingType.REPLICATOR).add(replicatorPlanet);
                                            float value = estimateCondition(condition);
                                            if (value < bestValueOfCondition) {
//                                                System.out.println("first " + value + " " + bestValueOfCondition + " " + condition);
                                                bestCondition = copyMap(condition);
//                                                System.out.println("second " + value + " " + bestValueOfCondition + " " + bestCondition);
                                                bestValueOfCondition = estimateCondition(bestCondition);
                                            }
                                        }
                                        used[accumulatorPlanet] = false;
                                    }
                                    used[chipPlanet] = false;
                                }
                                used[furnacePlanet] = false;
                                used[firstFoundryPlanet] = false;
                                used[secondFoundryPlanet] = false;
                            }
                            used[plasticPlanet] = false;
                        }
                        used[orePlanet] = false;
                    }
                    used[sandPlanet] = false;
                }
                used[organicPlanet] = false;
            }
            System.out.println("end" + bestCondition + " " + bestValueOfCondition);
            condition = copyMap(bestCondition);
        }

        Map<BuildingType, List<Integer>> genBySimulatedAnnealing() {
            bestCondition = copyMap(condition);
            bestValueOfCondition = estimateCondition(bestCondition);
            float temp = 1f;
            float value = estimateCondition(condition);
            return bestCondition;
            /*
            for (int i = 0; i < 200; i++) {
                for (int j = 0; j < 60; j++) {
                    genNewCondition();
                    float newValue = estimateCondition(newCondition);
                    double probability = Math.exp(-(value - newValue) / (10f * temp));

                    if (newValue > value || probability > random.nextDouble()) {
                        condition = copyMap(newCondition);
                        value = newValue;
                    }
                    if (value < bestValueOfCondition) {
//                        System.out.println(value + " " + bestValueOfCondition + " " + condition);
                        bestValueOfCondition = value;
                        bestCondition = copyMap(condition);
                    }
                }
                temp *= 0.95;
            }
            System.out.println(bestCondition + " " + bestValueOfCondition);
            return bestCondition;

             */
        }

        private Map<BuildingType, List<Integer>> copyMap(Map<BuildingType, List<Integer>> original) {
            Map<BuildingType, List<Integer>> copy = new TreeMap<>();
            for (Map.Entry<BuildingType, List<Integer>> entry : original.entrySet()) {
                copy.put(entry.getKey(),
                        // Or whatever List implementation you'd like here.
                        new ArrayList<>(entry.getValue()));
            }
            return copy;
        }

        private void genNewCondition() {
            boolean[] newUsed = new boolean[planets.length];
            newUsed[homePlanet] = true;
            newCondition = new TreeMap<>(condition);
            for (int i = 1; i < firstStageCount; i++) {
                BuildingType buildingType = BuildingType.values()[i];
                int limit = getBuildingLimit(buildingType);
                newCondition.put(buildingType, new ArrayList<>());
                for (int j = 0; j < limit; j++) {
                    int newPlace = findNewPlaceFirstStage(condition.get(buildingType).get(j), buildingType);
                    if (!newUsed[newPlace]) {
                        newCondition.get(buildingType).add(newPlace);
                        newUsed[newPlace] = true;
                    } else {
                        j--;
                    }
                }
            }
            for (int i = firstStageCount; i < BuildingType.values().length; i++) {
                BuildingType buildingType = BuildingType.values()[i];
                int limit = getBuildingLimit(buildingType);
                newCondition.put(buildingType, new ArrayList<>());
                for (int j = 0; j < limit; j++) {
                    int newPlace = findNewPlaceSecondStage(condition.get(buildingType).get(j), newUsed);
                    if (!newUsed[newPlace]) {
                        newCondition.get(buildingType).add(newPlace);
                        newUsed[newPlace] = true;
                    } else {
                        j--;
                    }
                }
            }
        }

        private int findNewPlaceFirstStage(int place, BuildingType buildingType) {
            if (random.nextDouble() < 0.1) {
                return place;
            }
            Resource resource = quarryProperties.get(buildingType).getProduceResource();
            ArrayList<Integer> planetsWithRes = myResourceMap.get(resource);
            return planetsWithRes.get(random.nextInt(planetsWithRes.size()));

        }

        private int findNewPlaceSecondStage(int place, boolean[] newUsed) {
            if (random.nextDouble() < 0.25) {
                return place;
            }
            ArrayList<Integer> places = new ArrayList<>();
            for (int i = 1; i < planets.length; i++) {
                int thisPlanet = nearPlanets[place][i];
                if (!newUsed[thisPlanet]) places.add(thisPlanet);
                if (places.size() >= 10) break;
            }
            return places.get(random.nextInt(places.size()));
        }

    }
}


