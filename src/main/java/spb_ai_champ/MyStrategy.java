package spb_ai_champ;

import spb_ai_champ.model.*;
import spb_ai_champ.myClasses.Event;
import spb_ai_champ.myClasses.Pair;

import java.util.*;

public class MyStrategy {

    final Random random = new Random();
    final Map<Resource, Integer> myResources = new TreeMap<>();
    final Map<Resource, ArrayList<Integer>> myDevelopedResourceMap = new TreeMap<>();
    final Map<Resource, ArrayList<Integer>> myResourceMap = new TreeMap<>();
    final Map<Resource, ArrayList<BuildingType>> recyclingBuilding = new TreeMap<>();
    final int staticDefenders = 2;
    final int firstStageCount = 4;
    Map<BuildingType, ArrayList<Integer>> myBuildingPlans;
    ArrayList<MoveAction> moveActions;
    ArrayList<BuildingAction> buildActions;
    Set<Integer> myPlanets = new TreeSet<>();
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
    int phase;
    int built;
    int buildPlanned;
    int homePlanet;
    int enemyPlanet;
    int oreLimit;
    int myRobots;
    PriorityQueue<Event> events;
    ArrayList<Integer> myResourcePlanets = new ArrayList<>();


    void start() {
        if (game.getCurrentTick() == 0) {
            phase = 0;
            built = 0;
            events = new PriorityQueue<>(1);
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
                    if (planet.getHarvestableResource() == null) {
                        continue;
                    }
                    myResourceMap.get(planet.getHarvestableResource()).add(planetIndex);
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
                    Comparator<Integer> planetDistanceComp = new Comparator<Integer>() {
                        @Override
                        public int compare(Integer o1, Integer o2) {
                            return Integer.compare(planetsDistance[o1][finalPlanetIndex], planetsDistance[o2][finalPlanetIndex]);
                        }
                    };
                    arrayList.sort(planetDistanceComp);
                    int[] arr = new int[planets.length];
                    for (int i = 0; i < planets.length; i++) {
                        arr[i] = arrayList.get(i);
                    }
                    nearPlanets[planetIndex] = arr;
                }
            } //near planets
            {
                myBuildingPlans = new TreeMap<>();
                for (BuildingType buildingType :
                        BuildingType.values()) {
                    myBuildingPlans.put(buildingType, new ArrayList<>());
                }
                choosePlanetsForFirstStage(BuildingType.MINES, oreLimit);
                choosePlanetsForFirstStage(BuildingType.CAREER, 1);
                choosePlanetsForFirstStage(BuildingType.FARM, 1);
                for (int building = 4; building <= 6; building++) {
                    choosePlanetsForSecondStage(BuildingType.values()[building]);
                }
                for (int building = 7; building < BuildingType.values().length; building++) {
                    choosePlanetForThirdStage(BuildingType.values()[building]);
                }
            }
        } else if (game.getCurrentTick() == 1) {
            {
                myBuildingPlans.remove(BuildingType.QUARRY);
                BuildPlansGenerator buildPlansGenerator = new BuildPlansGenerator(myBuildingPlans);
                myBuildingPlans = new TreeMap<>(buildPlansGenerator.genBySimulatedAnnealing());
                myBuildingPlans.put(BuildingType.QUARRY, new ArrayList<>());
                plannedBuilding = new BuildingType[planets.length];
                for (BuildingType buildingType : BuildingType.values()) {
                    for (int planetIndex : myBuildingPlans.get(buildingType)) {
                        plannedBuilding[planetIndex] = buildingType;
                        buildPlanned++;
                    }
                }
                //System.out.println(myBuildingPlans);
            } //fill myBuildingPlans
        } else if (game.getCurrentTick() == 2) {
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
        }

        //System.out.println(recyclingBuilding);
        //System.out.println(logisticGraph);
        //System.out.println(reversedLogisticGraph);

    }

    void sendRobotsToMyPlanets() {
        for (int planetIndex : myPlanets) {
            moveActions.add(new MoveAction(homePlanet, planetIndex, staticDefenders, null));
        }
    }

    void getEvents(Map<Integer, ArrayList<Event>> eventsMap) {
        while (events.peek() != null && events.peek().getTick() == game.getCurrentTick()) {
            Event event = events.poll();
            eventsMap.putIfAbsent(event.getPlanet(), new ArrayList<>());
            eventsMap.get(event.getPlanet()).add(event);
        }
    }

    void setEvent(MoveAction moveAction, ArrayList<MoveAction> newMoveActions) {
        if (isEnemyPlanet(moveAction.getStartPlanet())) {
            events.add(new Event(game.getCurrentTick() + 1,
                    moveAction.getStartPlanet(), moveAction.getTargetPlanet(), moveAction.getTakeResource(),
                    moveAction.getWorkerNumber()));
        }
        if (planetsGraph[moveAction.getStartPlanet()][moveAction.getTargetPlanet()] == -1) {
            int nextPlanet = findNextPlanet(moveAction.getStartPlanet(), moveAction.getTargetPlanet());
            events.add(new Event(game.getCurrentTick() + planetsDistance[moveAction.getStartPlanet()][nextPlanet],
                    nextPlanet, moveAction.getTargetPlanet(), moveAction.getTakeResource(),
                    moveAction.getWorkerNumber()));
            newMoveActions.add(new MoveAction(moveAction.getStartPlanet(), nextPlanet, moveAction.getWorkerNumber(),
                    moveAction.getTakeResource()));
        } else {
            newMoveActions.add(moveAction);
        }
    }

    int findNextPlanet(int planet, int endPlanet) {
        int minDistance = 1000;
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
        if (game.getCurrentTick() == 0 || game.getCurrentTick() == 1) {
            start();
            return new Action(new MoveAction[0], new BuildingAction[0], Specialty.LOGISTICS);
        } else if (game.getCurrentTick() == 2) {
            start();
            sendRobotsToMyPlanets();
        }
        if (phase == 0 && built == buildPlanned) {
            //System.out.println("New phase");
            phase = 1;
        }
        Map<Integer, ArrayList<Event>> eventsMap = new TreeMap<>();
        getEvents(eventsMap);
        if (phase == 0) {
            int myStone = planets[homePlanet].getResources().getOrDefault(Resource.STONE, 0);
            int myWorkers = countRobotsOnPlanet(homePlanet);
            myWorkers -= staticDefenders;
            for (BuildingType buildingType : BuildingType.values()) {
                for (int nextPlanetIndex : myBuildingPlans.get(buildingType)) {
                    if (planets[nextPlanetIndex].getBuilding() == null) {
                        if (game.getCurrentTick() > 40) {
                            sentStone[nextPlanetIndex] = 0;
                        }
                        int sendCount = Math.min(myWorkers, -(sentStone[nextPlanetIndex] - 20) +
                                quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE));
                        sendCount = Math.max(sendCount, 0);
                        if (myStone >= sendCount &&
                                sentStone[nextPlanetIndex] - 20 < quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE)) {
                            moveActions.add(new MoveAction(homePlanet, nextPlanetIndex, sendCount, Resource.STONE));
                            sentStone[nextPlanetIndex] += sendCount;
                            myWorkers -= sendCount;
                            myStone -= sendCount;
                        }
                    }
                }
            }
        }
        built = 0;
        for (BuildingType buildingType : BuildingType.values()) {
            for (int planetIndex : myBuildingPlans.get(buildingType)) {
                if (planets[planetIndex].getResources().getOrDefault(Resource.STONE, 0) >=
                        quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE) &&
                        planets[planetIndex].getBuilding() == null) {
                    buildActions.add(new BuildingAction(planetIndex, buildingType));
                }
                if (planets[planetIndex].getBuilding() != null) {
                    built++;
                }
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
            int myWorkers = countRobotsOnPlanet(planetIndex);
            myWorkers -= myPlanets.contains(planetIndex) ? staticDefenders : 0;

            if (myWorkers <= 0) continue;

            for (Event event : eventsMap.getOrDefault(planetIndex, new ArrayList<>())) {
                int robots = Math.min(event.getRobots(), myWorkers);
                if (event.getResource() != null) {
                    robots = Math.min(robots, planets[planetIndex].getResources().getOrDefault(event.getResource(),
                            0));
                }
                moveActions.add(new MoveAction(planetIndex, event.getEndPlanet(), robots, event.getResource()));
                myWorkers -= robots;
            }

            if (myWorkers <= 0) continue;

            if (phase == 0 && planetIndex != homePlanet) {
                moveActions.add(new MoveAction(planetIndex, homePlanet, myWorkers, null));
            }

            if (phase == 0) continue;

            int cachedWorkers = myWorkers;
            if (planets[planetIndex].getBuilding() != null && planetIndex != homePlanet && myPlanets.contains(planetIndex)) {
                BuildingType buildingType = planets[planetIndex].getBuilding().getBuildingType();
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
                        Pair<Integer, Integer> k = entry.getKey();
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
                    //System.out.println(moveActions.get(moveActions.size() - 1));
                }
            } else {
                if (myWorkers > 1) {
                    if (myPlanets.contains(planetIndex)) {
                        myWorkers -= (myWorkers) / 2;
                        myWorkers += findPlanetToSendRobots(planetIndex, (myWorkers) / 2);
                        shipment(planetIndex, myWorkers);
                    } else {
                        findPlanetToSendRobots(planetIndex, myWorkers);
                    }
                }
            }
        }

        checkFlyingGroups();
        MoveAction[] moveActionsArray = new MoveAction[moveActions.size()];
        BuildingAction[] buildingActionsArray = new BuildingAction[buildActions.size()];
        return new Action(moveActions.toArray(moveActionsArray), buildActions.toArray(buildingActionsArray), null);
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
        if (myRobots > 1100) {
            int attackPlanet = findPlanetToAttack(planetIndex);
            if (attackPlanet == -1) {
                return robots;
            }
            moveActions.add(new MoveAction(planetIndex, findNextPlanet(planetIndex, attackPlanet),
                    robots, null));
            robots = 0;
        }
        return robots;
    }

    int findPlanetToAttack(int planetIndex) {
        for (int i = 0; i < planets.length; i++) {
            int thisPlanet = nearPlanets[planetIndex][i];
            if (isEnemyPlanet(thisPlanet)) {
                return thisPlanet;
            }
        }
        return -1;
    }

    boolean isEnemyPlanet(int planetIndex) {
        for (WorkerGroup workerGroup : planets[planetIndex].getWorkerGroups()) {
            if (workerGroup.getPlayerIndex() != game.getMyIndex()) {
                return true;
            }
        }
        return false;
    }

    void countMyRobots() {
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
            i++;
            if (moveAction.getStartPlanet() == moveAction.getTargetPlanet()) continue;
            setEvent(moveAction, newMoveActions);
        }
        while (i < moveActions.size()) {
            MoveAction moveAction = moveActions.get(i);
            i++;
            if (moveAction.getStartPlanet() == moveAction.getTargetPlanet()) continue;
            events.add(new Event(game.getCurrentTick() + 1,
                    moveAction.getStartPlanet(), moveAction.getTargetPlanet(), moveAction.getTakeResource(),
                    moveAction.getWorkerNumber()));
        }
        moveActions = newMoveActions;
    }

    void shipment(int planetIndex, int myWorkers) {
        int planetRobots = myWorkers;
        if (planetRobots / myResourcePlanets.size() <= 0) return;
        for (int indexPlanet : myResourcePlanets) {
            moveActions.add(new MoveAction(planetIndex, indexPlanet, planetRobots / myResourcePlanets.size(), null));
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
        BuildingType startPlanetBuilding = planets[startPlanetIndex].getBuilding().getBuildingType();
        BuildingType endPlanetBuilding = planets[endPlanetIndex].getBuilding().getBuildingType();
        //System.out.print(startPlanetBuilding + " " + endPlanetBuilding + " " + endPlanetIndex + " " + robots + " " + reversed + " ");

        if (!reversed) {
            Resource resToSend = quarryProperties.get(startPlanetBuilding).getProduceResource();
            value += (1f / 5f) * (calcShipmentScore(endPlanetIndex, resToSend, robots) -
                    calcShipmentScore(endPlanetIndex, resToSend, 0));
            //System.out.print(value + " ");

            value += (1f / 5f) * planets[startPlanetIndex].getResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");
            value += -(1f / 5f) * planets[endPlanetIndex].getResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");

            if (endPlanetBuilding.tag < 9) {
                value -= (1f / 200f) * Math.max(myResources.getOrDefault(quarryProperties.get(endPlanetBuilding).getProduceResource(), 0) - 100, 0);
            }
            //System.out.print(value + " ");
        } else {
            Resource resToSend = quarryProperties.get(endPlanetBuilding).getProduceResource();
            value += -(1f / 2f) * Math.max(-1, (countRobotsOnPlanet(endPlanetIndex) -
                    quarryProperties.get(planets[endPlanetIndex].getBuilding().getBuildingType()).getMaxWorkers()));
            //System.out.print(value + " ");
            value += (1f / 10f) * (calcShipmentScore(startPlanetIndex, resToSend, robots) -
                    calcShipmentScore(startPlanetIndex, resToSend, 0));
            //System.out.print(value + " ");
            value -= (1f / 60f) * planets[startPlanetIndex].getResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");
            value -= (1f / 1f) *
                    quarryProperties.get(startPlanetBuilding).getWorkResources().getOrDefault(resToSend, 0);
            //System.out.print(value + " ");

//            if (planets[endPlanetIndex].getResources().getOrDefault(resToSend, 0) > 50) {
//                value -= (2f / 1f) * countRobotsOnPlanet(endPlanetIndex);
//            }
        }
        //System.out.println();

        return value;
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

    BuildingType producedBuilding(Resource resource) {
        if (resource == null) {
            return null;
        }

        return switch (resource) {
            case STONE -> BuildingType.QUARRY;
            case ORE -> BuildingType.MINES;
            case SAND -> BuildingType.CAREER;
            case ORGANICS -> BuildingType.FARM;
            case METAL -> BuildingType.FOUNDRY;
            case SILICON -> BuildingType.FURNACE;
            case PLASTIC -> BuildingType.BIOREACTOR;
            case CHIP -> BuildingType.CHIP_FACTORY;
            case ACCUMULATOR -> BuildingType.ACCUMULATOR_FACTORY;
        };
    }

    Resource resourceOfFirstStageBuilding(BuildingType buildingType) {
        return switch (buildingType) {
            case QUARRY -> Resource.STONE;
            case MINES -> Resource.ORE;
            case CAREER -> Resource.SAND;
            case FARM -> Resource.ORGANICS;
            default -> throw new IllegalStateException("Unexpected value: " + buildingType);
        };
    }

    void sort(int[][] array) {
        for (int i = 1; i < array.length; i++) {
            int j = i;
            while (j > 0 && (array[j][0] < array[j - 1][0] || (array[j][0] == array[j - 1][0] && array[j][1] < array[j - 1][1]))) {
                int[] tmp = array[j];
                array[j] = array[j - 1];
                array[j - 1] = tmp;
                j--;
            }
        }
    }

    void choosePlanetsForFirstStage(BuildingType building, int limit) {
        ArrayList<Integer> resourcesIndexes = myResourceMap.get(resourceOfFirstStageBuilding(building));
        int[][] planetsValue = new int[resourcesIndexes.size()][];
        for (int planetIndex = 0; planetIndex < resourcesIndexes.size(); planetIndex++) {
            int value = 0;
            value -= 2 * planetsDistance[homePlanet][resourcesIndexes.get(planetIndex)];
            value += planetsDistance[enemyPlanet][resourcesIndexes.get(planetIndex)];
            planetsValue[planetIndex] = new int[]{value, resourcesIndexes.get(planetIndex)};
        }
        sort(planetsValue);
        for (int i = resourcesIndexes.size() - 1; i >= Math.max(0, resourcesIndexes.size() - limit); i--) {
            myBuildingPlans.get(building).add(planetsValue[i][1]);
        }
    }

    BuildingType firstStageBuilding(BuildingType building) {
        return switch (building) {
            case FOUNDRY -> BuildingType.MINES;
            case FURNACE -> BuildingType.CAREER;
            case BIOREACTOR -> BuildingType.FARM;
            default -> throw new IllegalStateException("Unexpected value: " + building);
        };
    }

    void choosePlanetsForSecondStage(BuildingType building) {
        int lookingPlanets = planets.length;
        for (int firstStagePlanetIndex : myBuildingPlans.get(firstStageBuilding(building))) {
            int loop = switch (building) {
                case FOUNDRY -> 2;
                default -> 1;
            };
            for (int looped = 0; looped < loop; looped++) {
                int[] planetValue = new int[lookingPlanets];
                for (int i = 0; i < lookingPlanets; i++) {
                    int secondStagePlanetIndex = nearPlanets[firstStagePlanetIndex][i];
                    boolean planned = secondStagePlanetIndex == homePlanet;
                    for (BuildingType buildingType : BuildingType.values()) {
                        if (myBuildingPlans.get(buildingType).contains(secondStagePlanetIndex)) {
                            planned = true;
                            break;
                        }
                    }
                    if (planned) {
                        planetValue[i] = -10000;
                        continue;
                    }
                    int value = 0;
                    value -= 9 * planetsDistance[firstStagePlanetIndex][secondStagePlanetIndex];
                    if (planetsGraph[firstStagePlanetIndex][secondStagePlanetIndex] == -1) {
                        value -= 20;
                    }
                    value -= 3 * planetsDistance[homePlanet][secondStagePlanetIndex];
                    value += 3 * planetsDistance[enemyPlanet][secondStagePlanetIndex];
                    planetValue[i] = value;
                }
                int maxi = 0;
                for (int i = 0; i < lookingPlanets; i++) {
                    if (planetValue[i] > planetValue[maxi]) {
                        maxi = i;
                    }
                }
                myBuildingPlans.get(building).add(nearPlanets[firstStagePlanetIndex][maxi]);
            }
        }
    }

    void choosePlanetForThirdStage(BuildingType building) {
        if (building == BuildingType.REPLICATOR2 || building == BuildingType.REPLICATOR3) return;
        int[] planetValues = new int[planets.length];
        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            boolean planned = planetIndex == homePlanet;
            for (BuildingType buildingType : BuildingType.values()) {
                if (myBuildingPlans.get(buildingType).contains(planetIndex)) {
                    planned = true;
                    break;
                }
            }
            if (planned) {
                planetValues[planetIndex] = -1000000;
                continue;
            }
            int value = 0;
            for (Resource resource : quarryProperties.get(building).getWorkResources().keySet()) {
                BuildingType buildingType = producedBuilding(resource);
                int sum = 0;
                for (int previousPlanetIndex : myBuildingPlans.get(buildingType)) {
                    sum += planetsDistance[planetIndex][previousPlanetIndex];
                }
                value -= 5 * quarryProperties.get(building).getWorkResources().get(resource) * sum / myBuildingPlans.get(buildingType).size();
            }
            value -= 2 * planetsDistance[enemyPlanet][planetIndex];
            value += 2 * planetsDistance[homePlanet][planetIndex];
            planetValues[planetIndex] = value;
        }
        int maxi = 0;
        for (int i = 0; i < planets.length; i++) {
            if (planetValues[i] > planetValues[maxi]) {
                maxi = i;
            }
        }
        myBuildingPlans.get(building).add(maxi);
    }

    boolean compareResources(Map<Resource, Integer> a, Map<Resource, Integer> b) {
        boolean ans = true;
        for (Resource resource : a.keySet()) {
            if (a.get(resource) > b.getOrDefault(resource, 0)) {
                ans = false;
            }
        }
        return ans;
    }

    class BuildPlansGenerator {

        Map<BuildingType, ArrayList<Integer>> condition;
        Map<BuildingType, ArrayList<Integer>> newCondition = new TreeMap<>();
        Map<BuildingType, ArrayList<Integer>> bestCondition = new TreeMap<>();
        float bestValueOfCondition = -100000f;

        BuildPlansGenerator(Map<BuildingType, ArrayList<Integer>> condition) {
            this.condition = condition;
        }

        int getBuildingLimit(BuildingType buildingType) {
            return switch (buildingType) {
                case FOUNDRY -> 2;
                case QUARRY, REPLICATOR2, REPLICATOR3 -> 0;
                default -> 1;
            };

        }

        float estimateCondition(Map<BuildingType, ArrayList<Integer>> condition) {
            float value = 0;
            for (BuildingType sender : BuildingType.values()) {
                Resource sendRes = quarryProperties.get(sender).getProduceResource();
                if (sendRes == null) continue;
                for (BuildingType recycler : recyclingBuilding.get(sendRes)) {
                    for (int senderIndex : condition.get(sender)) {
                        for (int recyclerIndex : condition.get(recycler)) {
                            value += -(4f) * quarryProperties.get(recycler).getWorkResources().get(sendRes) * planetsDistance[senderIndex][recyclerIndex];
                        }
                        value += -(1f / 15f) * (planetsDistance[homePlanet][senderIndex]);
                    }
                }
            }
            return value;
        }

        void genFirstCondition() {
            boolean[] usedPlanet = new boolean[planets.length];
            usedPlanet[homePlanet] = true;
            condition = new TreeMap<>();
            for (int i = 1; i < firstStageCount; i++) {
                BuildingType buildingType = BuildingType.values()[i];
                Resource resource = quarryProperties.get(buildingType).getProduceResource();
                ArrayList<Integer> planetsWithRes = myResourceMap.get(resource);
                int limit = getBuildingLimit(buildingType);
                condition.put(buildingType, new ArrayList<>());
                for (int j = 0; j < limit; j++) {
                    int place = planetsWithRes.get(random.nextInt(planetsWithRes.size()));
                    if (!usedPlanet[place]) {
                        condition.get(buildingType).add(place);
                        usedPlanet[place] = true;
                    } else {
                        j--;
                    }
                }
            }
            for (int i = firstStageCount; i < BuildingType.values().length; i++) {
                BuildingType buildingType = BuildingType.values()[i];
                int limit = getBuildingLimit(buildingType);
                condition.put(buildingType, new ArrayList<>());
                for (int j = 0; j < limit; j++) {
                    int place = random.nextInt(planets.length);
                    if (!usedPlanet[place]) {
                        condition.get(buildingType).add(place);
                        usedPlanet[place] = true;
                    } else {
                        j--;
                    }
                }
            }
        }

        Map<BuildingType, ArrayList<Integer>> genBySimulatedAnnealing() {
            if (condition == null) genFirstCondition();
            float temp = 1f;
            float value = estimateCondition(condition);
            for (int i = 0; i < 250; i++) {
                for (int j = 0; j < 60; j++) {
                    genNewCondition();
                    float newValue = estimateCondition(newCondition);
                    double probability = Math.exp(-(value - newValue) / (10f * temp));

                    if (newValue > value || probability > random.nextDouble()) {
                        updateConditionFromNewCondition();
                        value = estimateCondition(condition);
                    }
                    if (value > bestValueOfCondition) {
                        bestValueOfCondition = value;
                        bestCondition = new TreeMap<>(condition);
                    }
                }
                temp *= 0.93;
            }
            //System.out.println(bestCondition + " " + bestValueOfCondition);
            return bestCondition;
        }

        private void updateConditionFromNewCondition() {
            condition = new TreeMap<>(newCondition);
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
            if (random.nextDouble() < 0.3) {
                return place;
            }
            Resource resource = quarryProperties.get(buildingType).getProduceResource();
            ArrayList<Integer> planetsWithRes = myResourceMap.get(resource);
            return planetsWithRes.get(random.nextInt(planetsWithRes.size()));

        }

        private int findNewPlaceSecondStage(int place, boolean[] newUsed) {
            if (random.nextDouble() < 0.3) {
                return place;
            }
            ArrayList<Integer> places = new ArrayList<>();
            for (int i = 1; i < planets.length; i++) {
                int thisPlanet = nearPlanets[place][i];
                if (!newUsed[thisPlanet]) places.add(thisPlanet);
                if (places.size() >= 5) break;
            }
            return places.get(random.nextInt(places.size()));
        }

    }
}


