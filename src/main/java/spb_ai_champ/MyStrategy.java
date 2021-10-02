package spb_ai_champ;

import spb_ai_champ.model.*;

import java.util.*;

public class MyStrategy {

    private static final Comparator<Event> idComparator = Comparator.comparingInt(Event::getTick);
    final Random random = new Random();
    final Map<Resource, Integer> myResources = new TreeMap<>();
    final Map<Resource, ArrayList<Integer>> myDevelopedResourceMap = new TreeMap<>();
    final Map<Resource, ArrayList<Integer>> myResourceMap = new TreeMap<>();
    final Map<BuildingType, ArrayList<Integer>> myBuildingPlans = new TreeMap<>();
    final Map<Resource, ArrayList<BuildingType>> recyclingBuilding = new TreeMap<>();
    final int staticDefenders = 10;
    ArrayList<MoveAction> moveActions;
    ArrayList<BuildingAction> buildActions;
    Set<Integer> myPlanets = new TreeSet<>();
    Map<BuildingType, BuildingProperties> quarryProperties;
    Planet[] planets;
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
    int medianLine;
    int homePlanet;
    int enemyPlanet;
    int oreLimit;
    PriorityQueue<Event> events;
    int[] circleLimits;
    int[] circleRobots;
    ArrayList<Integer> myResourcePlanets = new ArrayList<>();


    private void start(Game game) {
        phase = 0;
        built = 0;
        events = new PriorityQueue<>(1, idComparator);
        quarryProperties = game.getBuildingProperties();
        sentStone = new int[planets.length];
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
            medianLine = 29;
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
            nearPlanets = new int[planets.length][planets.length];
            for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
                for (int i = 0; i < planets.length; i++) {
                    nearPlanets[planetIndex][i] = i;
                }
                insertionSortForPlanets(nearPlanets[planetIndex], planetIndex);
            }
        } //near planets
        {
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
            plannedBuilding = new BuildingType[planets.length];
            for (BuildingType buildingType : BuildingType.values()) {
                for (int planetIndex : myBuildingPlans.get(buildingType)) {
                    plannedBuilding[planetIndex] = buildingType;
                    buildPlanned++;
                }
            }
            System.out.println(myBuildingPlans);
        } //fill myBuildingPlans
        {
            /*
            oreCircle = 50; // 0
            chipCircle = 20; // 1
            accumulatorCircle = 10; // 2
            replicatorCircle = 10; // 3
            sandCircle = 50; // 4
            organicCircle = 50; // 5
            chipReplicatorCircle = 10; // 6
            accumulatorReplicatorCircle = 5; // 7
            plasticCircle = 10; // 8
            siliconCircle = 10; // 9
            */
            circleLimits = new int[]{200, 50, 50, 50, 50, 50, 40, 30, 40, 30};
            circleRobots = new int[circleLimits.length];
        } // group
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
        System.out.println(recyclingBuilding);
        System.out.println(logisticGraph);
        System.out.println(reversedLogisticGraph);

    }

    private void sendRobotsToMyPlanets() {
        for (int planetIndex : myPlanets) {
            moveActions.add(new MoveAction(homePlanet, planetIndex, staticDefenders, null));
        }
    }


    public Action getAction(Game game) {
        planets = game.getPlanets();
        moveActions = new ArrayList<>();
        buildActions = new ArrayList<>();
        boolean[] used = new boolean[planets.length];
        if (game.getCurrentTick() == 0) {
            start(game);
            sendRobotsToMyPlanets();
            used[homePlanet] = true;
        }
        if (phase == 0 && built == buildPlanned) {
            phase = 1;
        }
        int replicatorPlanet = myBuildingPlans.get(BuildingType.REPLICATOR).get(0);
        int replicatorPlanetRobots = 0;
        for (WorkerGroup workerGroup : planets[replicatorPlanet].getWorkerGroups()) {
            replicatorPlanetRobots += workerGroup.getNumber();
        }

        if (phase == 1) {
            for (int planetIndex : myPlanets) {
                int myWorkers = 0;
                for (WorkerGroup workerGroup : planets[planetIndex].getWorkerGroups()) {
                    myWorkers += workerGroup.getPlayerIndex() == game.getMyIndex() ? workerGroup.getNumber() : 0;
                }
                myWorkers = Math.max(0, myWorkers - staticDefenders);

                int DIVISOR_CONSTANT = 2;

                if (planets[planetIndex].getBuilding() != null && myWorkers > 0 && planetIndex != homePlanet) {
                    BuildingType buildingType = planets[planetIndex].getBuilding().getBuildingType();
                    Resource planetResourse = quarryProperties.get(buildingType).getProduceResource();
                    if (buildingType.tag >= 4) {
                        int toWork = productionForRes(planets[planetIndex].getResources(),
                                quarryProperties.get(buildingType).getWorkResources());
                        myWorkers -= Math.min(toWork * quarryProperties.get(buildingType).getProduceAmount(),
                                quarryProperties.get(buildingType).getMaxWorkers());
                    } else {
                        if (planets[planetIndex].getResources().getOrDefault(quarryProperties.get(buildingType).getProduceResource(), 0) < myWorkers / DIVISOR_CONSTANT) {
                            myWorkers = 0;
                        }
                    }
                    if (myWorkers <= 0) {
                        continue;
                    }
                    TreeMap<Integer, Float> calcValue = new TreeMap<>();
                    for (int endPlanetIndex : logisticGraph.get(planetIndex)) {
                        if (planets[planetIndex].getResources().getOrDefault(planetResourse, 0) > myWorkers / 3) {
                            calcValue.put(endPlanetIndex,
                                    calcShipmentValue(planetIndex, endPlanetIndex, myWorkers / DIVISOR_CONSTANT, false)
                            );
                        }
                    }
                    for (int endPlanetIndex : reversedLogisticGraph.get(planetIndex)) {
                        calcValue.put(endPlanetIndex,
                                calcShipmentValue(planetIndex, endPlanetIndex, myWorkers / DIVISOR_CONSTANT, true)
                        );
                    }
                    float mod = 10e10f;
                    for (float value : calcValue.values()) {
                        mod = Math.min(mod, value);
                    }
                    float finalMod = mod + 1;
                    calcValue.forEach((k, v) -> calcValue.put(k, v + finalMod));
                    final float[] maxValue = {-100000f};
                    final int[] maxPlanetIndex = {0};
                    calcValue.forEach((k, v) -> {
                        float value = v * random.nextFloat();
                        if (value > maxValue[0]) {
                            maxPlanetIndex[0] = k;
                            maxValue[0] = value;

                        }
                    });
                    int endPlanetIndex = maxPlanetIndex[0];
                    Resource resToSend;
                    if (logisticGraph.get(planetIndex).contains(endPlanetIndex)) {
                        resToSend = quarryProperties.get(buildingType).getProduceResource();
                    } else {
                        resToSend = null;
                    }
                    moveActions.add(new MoveAction(planetIndex, endPlanetIndex, myWorkers / DIVISOR_CONSTANT, resToSend));
                } else {
                    shipment(planetIndex);
                }
            }
        }

        for (BuildingType buildingType : BuildingType.values()) {
            for (int planetIndex : myBuildingPlans.get(buildingType)) {
                if (planets[planetIndex].getResources().getOrDefault(Resource.STONE, 0) >=
                        quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE) &&
                        planets[planetIndex].getBuilding() == null) {
                    buildActions.add(new BuildingAction(planetIndex, buildingType));
                }
            }
        }
        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            Planet planet = planets[planetIndex];
            int myWorkers = 0;
            for (WorkerGroup workerGroup : planet.getWorkerGroups()) {
                if (plannedBuilding[planetIndex] != null && planets[planetIndex].getResources().getOrDefault(Resource.STONE, 0) >=
                        quarryProperties.get(plannedBuilding[planetIndex]).getBuildResources().get(Resource.STONE) &&
                        planets[planetIndex].getBuilding() == null) {
                    buildActions.add(new BuildingAction(planetIndex, plannedBuilding[planetIndex]));
                    built++;
                }
                if (workerGroup.getPlayerIndex() == game.getMyIndex()) {
                    myWorkers += workerGroup.getNumber();
                    for (Resource resource : Resource.values()) {
                        myResources.put(resource, myResources.get(resource) + planet.getResources().getOrDefault(resource, 0));
                    }
                }
            }
            if (planet.getBuilding() != null && myWorkers > 0) {
                if (planet.getBuilding().getBuildingType().tag >= 4) {
                    myWorkers -= compareResources(planet.getResources(), quarryProperties.get(planet.getBuilding().getBuildingType()).getBuildResources())
                            ? 20 - staticDefenders : 0;
                } else if (planet.getBuilding().getBuildingType().tag == 0) {
                    myWorkers -= game.getCurrentTick() <= 50 ? 20 - staticDefenders : 0;
                } else {
                    myWorkers -= quarryProperties.get(planet.getBuilding().getBuildingType()).getMaxWorkers() / 2;
                }
            }
            if (0 != planet.getResources().getOrDefault(Resource.STONE, 0) && planet.getBuilding() == null) {
                myWorkers -= staticDefenders;
            }
            myWorkers -= 10;
            if (phase == 0) {
                if (myWorkers > 0) {
                    if (planetIndex != homePlanet) {
                        moveActions.add(new MoveAction(planetIndex, homePlanet, myWorkers, null));
                        used[planetIndex] = true;
                    } else {
                        for (BuildingType buildingType : BuildingType.values()) {
                            for (int nextPlanetIndex : myBuildingPlans.get(buildingType)) {
                                if (planets[nextPlanetIndex].getBuilding() == null) {
                                    int sendCount = Math.min(myWorkers,
                                            quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE) -
                                                    planets[nextPlanetIndex].getResources().getOrDefault(Resource.STONE, 0));
                                    sendCount = Math.max(sendCount, 0);
                                    sendCount = Math.min(sendCount, game.getMaxFlyingWorkerGroups());
                                    if (planet.getResources().getOrDefault(Resource.STONE, 0) >= sendCount &&
                                            sentStone[nextPlanetIndex] < quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE) &&
                                            !used[planetIndex]) {
                                        moveActions.add(new MoveAction(planetIndex, nextPlanetIndex, sendCount, Resource.STONE));
                                        sentStone[nextPlanetIndex] += sendCount;
                                        myWorkers -= sendCount;
                                        used[planetIndex] = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        MoveAction[] moveActionsArray = new MoveAction[moveActions.size()];
        BuildingAction[] buildingActionsArray = new BuildingAction[buildActions.size()];
        return new Action(moveActions.toArray(moveActionsArray), buildActions.toArray(buildingActionsArray));
    }

    private void shipment(int planetIndex) {
        int planetRobots = Math.max(0, Arrays.stream(planets[planetIndex].getWorkerGroups()).mapToInt(WorkerGroup::getNumber).sum() - staticDefenders);
        for (int indexPlanet : myResourcePlanets) {
            moveActions.add(new MoveAction(planetIndex, indexPlanet, planetRobots / myResourcePlanets.size(), null));
        }

    }

    private float siqmoid(float v) {
        return 1f / (1f + (float) Math.exp(v));
    }

    private BuildingType producedBuilding(Resource resource) {
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

    private Resource resourceOfFirstStageBuilding(BuildingType buildingType) {
        return switch (buildingType) {
            case QUARRY -> Resource.STONE;
            case MINES -> Resource.ORE;
            case CAREER -> Resource.SAND;
            case FARM -> Resource.ORGANICS;
            default -> throw new IllegalStateException("Unexpected value: " + buildingType);
        };
    }

    private BuildingType firstStageBuilding(BuildingType building) {
        return switch (building) {
            case FOUNDRY -> BuildingType.MINES;
            case FURNACE -> BuildingType.CAREER;
            case BIOREACTOR -> BuildingType.FARM;
            default -> throw new IllegalStateException("Unexpected value: " + building);
        };
    }

    private BuildingType resourceUsage(Resource resource) {
        return switch (resource) {
            case STONE, METAL, ORE -> null;
            case SAND -> BuildingType.FURNACE;
            case ORGANICS -> BuildingType.BIOREACTOR;
            case SILICON -> BuildingType.CHIP_FACTORY;
            case PLASTIC -> BuildingType.ACCUMULATOR_FACTORY;
            case CHIP, ACCUMULATOR -> BuildingType.REPLICATOR;
        };
    }

    private int productionForRes(Map<Resource, Integer> myResources, Map<Resource, Integer> resourceNeed) {
        int[] ans = new int[]{Integer.MAX_VALUE};
        resourceNeed.forEach((k, v) -> ans[0] = Math.min(ans[0], myResources.getOrDefault(k, 0) / v));
        return ans[0];
    }

    private void insertionSortForPlanets(int[] array, int planet) {
        for (int i = 1; i < array.length; i++) {
            int j = i;
            while (j > 0 && planetsDistance[j][planet] < planetsDistance[j - 1][planet]) {
                int tmp = array[j];
                array[j] = array[j - 1];
                array[j - 1] = tmp;
                j--;
            }
        }
    }

    private void sort(int[][] array) {
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

    private float calcShipmentValue(int startPlanetIndex, int endPlanetIndex, int robots, boolean reversed) {
        float value = 0;
        if (!reversed) {
            Resource resToSend = quarryProperties.get(planets[startPlanetIndex].getBuilding().getBuildingType()).getProduceResource();
            value += (1f / 1000f) * (calcShipmentScore(endPlanetIndex, resToSend, robots) -
                    calcShipmentScore(endPlanetIndex, resToSend, 0));
            value += -(1f / 50f) * planetsDistance[startPlanetIndex][endPlanetIndex];
            value += (1f / 10f) * planets[startPlanetIndex].getResources().getOrDefault(resToSend, 0);
        } else {
            Resource resToSend = quarryProperties.get(planets[endPlanetIndex].getBuilding().getBuildingType()).getProduceResource();
            //value += (1f / 10000f) * planets[endPlanetIndex].getResources().getOrDefault(resToSend, 0);
            value += -(1f / 30f) * planetsDistance[startPlanetIndex][endPlanetIndex];

        }
        return value;
    }

    private float calcShipmentScore(int planetIndex, Resource resource, int robots) {
        Map<Resource, Integer> planetResources = new TreeMap<>();
        planets[planetIndex].getResources().forEach(planetResources::put);
        planetResources.put(resource, planetResources.getOrDefault(resource, 0) + robots);
        return productionForRes(planetResources,
                quarryProperties.get(planets[planetIndex].getBuilding().getBuildingType()).getWorkResources());
    }

    private void choosePlanetsForFirstStage(BuildingType building, int limit) {
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

    private void choosePlanetsForSecondStage(BuildingType building) {
        int lookingPlanets = planets.length;
        for (int firstStagePlanetIndex : myBuildingPlans.get(firstStageBuilding(building))) {
            int loop = switch (building) {
                case FOUNDRY -> 3;
                case FURNACE -> 2;
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

    private void choosePlanetForThirdStage(BuildingType building) {
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

    private int findPlanetToSendResource(int senderPlanetIndex, Resource resource) {
        if (resource == Resource.STONE) {
            return -1;
        }
        if (resource == Resource.ORE) {
            int[] values = new int[1];
            for (int i = 0; i < 1; i++) {
                int foundryPlanetIndex = myBuildingPlans.get(BuildingType.FOUNDRY).get(i);
                Map<Resource, Integer> resources = planets[foundryPlanetIndex].getResources();
                values[i] -= 10 * planetsDistance[senderPlanetIndex][foundryPlanetIndex];
                values[i] += 5 * resources.getOrDefault(Resource.ORE, 0);
                values[i] += 4 * resources.getOrDefault(Resource.METAL, 0);
            }
            return myBuildingPlans.get(BuildingType.FOUNDRY).get(0);
            /*
            if (values[0] > values[1]) {
                return myBuildingPlans.get(BuildingType.FOUNDRY).get(0);
            }
            return myBuildingPlans.get(BuildingType.FOUNDRY).get(1);
            */

        }
        if (resource == Resource.METAL) {
            int chipFactoryValue = 0;
            int accumulatorFactoryValue = 0;
            int replicatorFactoryValue = 0;

            chipFactoryValue -= 3 * myResources.get(Resource.CHIP);
            chipFactoryValue += 2 * planets[myBuildingPlans.get(BuildingType.CHIP_FACTORY).get(0)].getResources().getOrDefault(Resource.SILICON, 0);
            chipFactoryValue -= 3 * planets[myBuildingPlans.get(BuildingType.CHIP_FACTORY).get(0)].getResources().getOrDefault(Resource.METAL, 0);

            accumulatorFactoryValue -= 5 * myResources.get(Resource.ACCUMULATOR);
            accumulatorFactoryValue += 2 * planets[myBuildingPlans.get(BuildingType.ACCUMULATOR_FACTORY).get(0)].getResources().getOrDefault(Resource.PLASTIC, 0);
            accumulatorFactoryValue -= 3 * planets[myBuildingPlans.get(BuildingType.ACCUMULATOR_FACTORY).get(0)].getResources().getOrDefault(Resource.METAL, 0);

            replicatorFactoryValue += 2 * planets[myBuildingPlans.get(BuildingType.REPLICATOR).get(0)].getResources().getOrDefault(Resource.CHIP, 0) +
                    planets[myBuildingPlans.get(BuildingType.REPLICATOR).get(0)].getResources().getOrDefault(Resource.ACCUMULATOR, 0);
            replicatorFactoryValue -= 4 * planets[myBuildingPlans.get(BuildingType.REPLICATOR).get(0)].getResources().getOrDefault(Resource.METAL, 0);

            if (replicatorFactoryValue >= accumulatorFactoryValue && replicatorFactoryValue >= chipFactoryValue) {
                return myBuildingPlans.get(BuildingType.REPLICATOR).get(0);
            } else if (chipFactoryValue >= accumulatorFactoryValue) {
                return myBuildingPlans.get(BuildingType.CHIP_FACTORY).get(0);
            }
            return myBuildingPlans.get(BuildingType.ACCUMULATOR_FACTORY).get(0);
        }
        return myBuildingPlans.get(resourceUsage(resource)).get(0);
    }

    private boolean compareResources(Map<Resource, Integer> a, Map<Resource, Integer> b) {
        boolean ans = true;
        for (Resource resource : a.keySet()) {
            if (a.get(resource) > b.getOrDefault(resource, 0)) {
                ans = false;
            }
        }
        return ans;
    }


}

