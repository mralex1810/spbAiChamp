package spb_ai_champ;

import spb_ai_champ.model.*;

import java.util.*;

public class MyStrategy {

    private static final Comparator<Event> idComparator = Comparator.comparingInt(Event::getTick);
    final Random random = new Random();
    final Map<Resource, Integer> myResources = new TreeMap<>();
    final Map<Resource, ArrayList<Integer>> myResourceMap = new TreeMap<>();
    final Map<Resource, ArrayList<Integer>> myDevelopedResourceMap = new TreeMap<>();
    final Map<BuildingType, ArrayList<Integer>> myBuildingPlans = new TreeMap<>();
    final Resource[] resources = new Resource[]{
            Resource.STONE, Resource.ORE, Resource.SAND, Resource.ORGANICS, Resource.METAL,
            Resource.SILICON, Resource.PLASTIC, Resource.CHIP, Resource.ACCUMULATOR};
    final BuildingType[] buildingTypes = new BuildingType[]{
            BuildingType.QUARRY, BuildingType.MINES, BuildingType.CAREER, BuildingType.FARM,
            BuildingType.FOUNDRY, BuildingType.FURNACE, BuildingType.BIOREACTOR,
            BuildingType.CHIP_FACTORY, BuildingType.ACCUMULATOR_FACTORY, BuildingType.REPLICATOR
    };
    Set<Integer> myPlanets = new TreeSet<>();
    Map<BuildingType, BuildingProperties> quarryProperties;
    Planet[] planets;
    int[][] nearPlanets;
    int[][] planetsGraph;
    int[][] planetsDistance;
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
    int staticDefenders;

    private void start(Game game) {
        phase = 0;
        built = 0;
        staticDefenders = 10;
        events = new PriorityQueue<>(1, idComparator);
        quarryProperties = game.getBuildingProperties();
        sentStone = new int[planets.length];
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
            oreLimit = 2;
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
                for (Resource resource : resources) {
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
                    buildingTypes) {
                myBuildingPlans.put(buildingType, new ArrayList<>());
            }
            choosePlanetsForFirstStage(BuildingType.MINES, oreLimit);
            choosePlanetsForFirstStage(BuildingType.CAREER, 1);
            choosePlanetsForFirstStage(BuildingType.FARM, 1);
            for (int building = 4; building <= 6; building++) {
                choosePlanetsForSecondStage(buildingTypes[building]);
            }
            for (int building = 7; building < buildingTypes.length; building++) {
                choosePlanetForThirdStage(buildingTypes[building]);
            }
            plannedBuilding = new BuildingType[planets.length];
            for (BuildingType buildingType : buildingTypes) {
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
            for (int planetIndex = 0;  planetIndex < planets.length; planetIndex++) {
                for (int myPlanetIndex = 0; myPlanetIndex < planets.length; myPlanetIndex++) {
                    if (planetsGraph[planetIndex][myPlanetIndex] != -1 && plannedBuilding[myPlanetIndex] != null) {
                        myPlanets.add(planetIndex);
                    }
                }
            }
        } // my planets
    }

    private void sendRobotsToMyPlanets( ArrayList<MoveAction> moveActions) {
        for (int planetIndex : myPlanets) {
            moveActions.add(new MoveAction(homePlanet, planetIndex, staticDefenders, null));
        }
    }

    private void startCircle(Game game, ArrayList<MoveAction> moveActions, int circle, int robots) {
        int endPlanet = myBuildingPlans.get(circleSender(circle)).get(circle == 0 ? Math.abs(random.nextInt()) % oreLimit : 0);
        events.add(new Event(game.getCurrentTick() + planetsDistance[homePlanet][endPlanet],
                endPlanet, circle, robots));
        moveActions.add(new MoveAction(homePlanet, endPlanet, robots, null));
    }

    public Action getAction(Game game) {
        Random random = new Random(System.nanoTime());
        planets = game.getPlanets();
        ArrayList<MoveAction> moveActions = new ArrayList<>();
        ArrayList<BuildingAction> buildActions = new ArrayList<>();
        boolean[] used = new boolean[planets.length];
        if (game.getCurrentTick() == 0) {
            start(game);
            sendRobotsToMyPlanets(moveActions);
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
            int homePlanetRobots = 0;
            for (WorkerGroup workerGroup : planets[homePlanet].getWorkerGroups()) {
                homePlanetRobots += workerGroup.getNumber();
            }
            for (int circle = 0; circle < circleLimits.length; circle++) {
                if (circleRobots[circle] < circleLimits[circle]) {
                    int sendRobots = Math.min(circleLimits[circle] - circleRobots[circle],
                            game.getMaxFlyingWorkerGroups());
                    sendRobots = Math.min(sendRobots, homePlanetRobots);
                    startCircle(game, moveActions, circle, sendRobots);
                    circleRobots[circle] += sendRobots;
                    used[homePlanet] = true;
                    break;
                }
            }

            if (replicatorPlanetRobots > 100 && game.getCurrentTick() > 80) {
                used[replicatorPlanet] = true;
                moveActions.add(new MoveAction(replicatorPlanet, homePlanet, replicatorPlanetRobots - 100, null));
                circleLimits[Math.abs(random.nextInt()) % circleLimits.length] += replicatorPlanetRobots - 100;
            }
        }
        for (BuildingType buildingType : buildingTypes) {
            for (int planetIndex : myBuildingPlans.get(buildingType)) {
                if (planets[planetIndex].getResources().getOrDefault(Resource.STONE, 0) >=
                        quarryProperties.get(buildingType).getBuildResources().get(Resource.STONE) &&
                        planets[planetIndex].getBuilding() == null) {
                    buildActions.add(new BuildingAction(planetIndex, buildingType));
                }
            }
        }
        int[] freeRobots = new int[planets.length];
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
                    for (Resource resource : resources) {
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
                        myWorkers = 0;
                    } else {
                        for (BuildingType buildingType : buildingTypes) {
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
            freeRobots[planetIndex] = Math.max(myWorkers, 0);
        }

        while (events.peek() != null && events.peek().getTick() <= game.getCurrentTick()) { //TODO
            Event event = events.poll();
            if (event != null && used[event.getPlanet()]) {
                events.add(new Event(event, 1));
                continue;
            }
            int nextPlanet = myBuildingPlans.get(circle(event.getPlanet(), event.getCircle())).get(
                    circle(event.getPlanet(), event.getCircle()) == BuildingType.FOUNDRY ||
                            circle(event.getPlanet(), event.getCircle()) == BuildingType.MINES ?
                            Math.abs(random.nextInt()) % oreLimit : 0);
            Resource resource = quarryProperties.get(circleSender(event.getCircle())).getProduceResource();
            if (planets[event.getPlanet()].getBuilding().getBuildingType() == circleSender(event.getCircle())) {
                if (planets[event.getPlanet()].getResources().getOrDefault(resource, 0) < circleLimits[event.getCircle()]) {
                    events.add(new Event(event, 5));
                } else {
                    events.add(new Event(game.getCurrentTick() + planetsDistance[event.getPlanet()][nextPlanet],
                            nextPlanet, event.getCircle(), event.getRobots()));
                    used[event.getPlanet()] = true;
                    moveActions.add(new MoveAction(event.getPlanet(), nextPlanet, event.getRobots(), resource));
                }
            } else {
                events.add(new Event(game.getCurrentTick() + planetsDistance[event.getPlanet()][nextPlanet],
                        nextPlanet, event.getCircle(), event.getRobots()));
                used[event.getPlanet()] = true;
                moveActions.add(new MoveAction(event.getPlanet(), nextPlanet, event.getRobots(), null));
            }
        }


        MoveAction[] moveActionsArray = new MoveAction[moveActions.size()];
        BuildingAction[] buildingActionsArray = new BuildingAction[buildActions.size()];
        return new Action(moveActions.toArray(moveActionsArray), buildActions.toArray(buildingActionsArray));
    }

    private boolean equals(int[] circleLimits, int[] circleRobots) {
        for (int i = 0; i < circleLimits.length; i++) {
            if (circleLimits[i] != circleRobots[i]) {
                return false;
            }
        }
        return true;
    }

    private BuildingType circle(int planetIndex, int circleIndex) {
        return switch (circleIndex) {
            case 0 -> switch (plannedBuilding[planetIndex]) {
                case MINES -> BuildingType.FOUNDRY;
                case FOUNDRY -> BuildingType.MINES;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 1 -> switch (plannedBuilding[planetIndex]) {
                case CHIP_FACTORY -> BuildingType.FOUNDRY;
                case FOUNDRY -> BuildingType.CHIP_FACTORY;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 2 -> switch (plannedBuilding[planetIndex]) {
                case ACCUMULATOR_FACTORY -> BuildingType.FOUNDRY;
                case FOUNDRY -> BuildingType.ACCUMULATOR_FACTORY;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 3 -> switch (plannedBuilding[planetIndex]) {
                case REPLICATOR -> BuildingType.FOUNDRY;
                case FOUNDRY -> BuildingType.REPLICATOR;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 4 -> switch (plannedBuilding[planetIndex]) {
                case CAREER -> BuildingType.FURNACE;
                case FURNACE -> BuildingType.CAREER;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 5 -> switch (plannedBuilding[planetIndex]) {
                case FARM -> BuildingType.BIOREACTOR;
                case BIOREACTOR -> BuildingType.FARM;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 6 -> switch (plannedBuilding[planetIndex]) {
                case CHIP_FACTORY -> BuildingType.REPLICATOR;
                case REPLICATOR -> BuildingType.CHIP_FACTORY;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 7 -> switch (plannedBuilding[planetIndex]) {
                case ACCUMULATOR_FACTORY -> BuildingType.REPLICATOR;
                case REPLICATOR -> BuildingType.ACCUMULATOR_FACTORY;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 8 -> switch (plannedBuilding[planetIndex]) {
                case BIOREACTOR -> BuildingType.ACCUMULATOR_FACTORY;
                case ACCUMULATOR_FACTORY -> BuildingType.BIOREACTOR;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            case 9 -> switch (plannedBuilding[planetIndex]) {
                case FURNACE -> BuildingType.CHIP_FACTORY;
                case CHIP_FACTORY -> BuildingType.FURNACE;
                default -> throw new IllegalStateException("Unexpected value: " + plannedBuilding[planetIndex]);
            };
            default -> throw new IllegalStateException("Unexpected value: " + circleIndex);
        };
    }

    private BuildingType circleSender(int circle) {
        return switch (circle) {
            case 0 -> BuildingType.MINES;
            case 1, 2, 3 -> BuildingType.FOUNDRY;
            case 4 -> BuildingType.CAREER;
            case 5 -> BuildingType.FARM;
            case 6 -> BuildingType.CHIP_FACTORY;
            case 7 -> BuildingType.ACCUMULATOR_FACTORY;
            case 8 -> BuildingType.BIOREACTOR;
            case 9 -> BuildingType.FURNACE;
            default -> throw new IllegalStateException("Unexpected value: " + circle);
        };
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
            int[] planetValue = new int[lookingPlanets];
            for (int i = 0; i < lookingPlanets; i++) {
                int secondStagePlanetIndex = nearPlanets[firstStagePlanetIndex][i];
                boolean planned = secondStagePlanetIndex == homePlanet;
                for (BuildingType buildingType : buildingTypes) {
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

    private void choosePlanetForThirdStage(BuildingType building) {
        int[] planetValues = new int[planets.length];
        for (int planetIndex = 0; planetIndex < planets.length; planetIndex++) {
            boolean planned = planetIndex == homePlanet;
            for (BuildingType buildingType : buildingTypes) {
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

