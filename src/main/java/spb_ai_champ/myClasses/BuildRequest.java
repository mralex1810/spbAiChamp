package spb_ai_champ.myClasses;

import spb_ai_champ.model.BuildingProperties;
import spb_ai_champ.model.BuildingType;
import spb_ai_champ.model.Resource;

import java.util.Map;

public class BuildRequest implements Comparable<BuildRequest> {
    private final int tickStarted;
    private final int planetIndex;
    private final BuildingType buildingType;
    private final Map<BuildingType, BuildingProperties> quarryProperties;
    private int sentRobotsToStone;
    private int sentRobotsFromStone;

    public BuildRequest(int tickStarted, int planetIndex, BuildingType buildingType, Map<BuildingType, BuildingProperties> quarryProperties) {
        this.tickStarted = tickStarted;
        this.planetIndex = planetIndex;
        this.buildingType = buildingType;
        this.quarryProperties = quarryProperties;
    }

    public int getTickStarted() {
        return tickStarted;
    }

    public int getPlanetIndex() {
        return planetIndex;
    }

    public BuildingType getBuildingType() {
        return buildingType;
    }


    public int getSentRobotsToStone() {
        return sentRobotsToStone;
    }

    public void setSentRobotsToStone(int sentRobotsToStone) {
        this.sentRobotsToStone = sentRobotsToStone;
    }

    public int getSentRobotsFromStone() {
        return sentRobotsFromStone;
    }

    public void setSentRobotsFromStone(int sentRobotsFromStone) {
        this.sentRobotsFromStone = sentRobotsFromStone;
    }

    public int needSendToStone() {
        return
                Math.max(0, quarryProperties.get(buildingType).getBuildResources().getOrDefault(Resource.STONE, 0) - sentRobotsToStone);
    }

    public int needSendFromStone() {
        return Math.min(sentRobotsToStone - sentRobotsFromStone,
                quarryProperties.get(buildingType).getBuildResources().getOrDefault(Resource.STONE, 0) - sentRobotsFromStone);
    }

    @Override
    public int compareTo(BuildRequest buildRequest) {
        if (buildingType == buildRequest.buildingType && planetIndex == buildRequest.getPlanetIndex()) {
            return 0;
        }
        if (buildingType.tag - buildRequest.getBuildingType().tag != 0) {
            return buildingType.tag - buildRequest.getBuildingType().tag;
        } else if (tickStarted - buildRequest.tickStarted != 0) {
            return tickStarted - buildRequest.tickStarted;
        }
        return planetIndex - buildRequest.getPlanetIndex();
    }

    @Override
    public String toString() {
        return "BuildRequest{" +
                "planetIndex=" + planetIndex +
                ", buildingType=" + buildingType +
                ", sentRobotsToStone=" + sentRobotsToStone +
                ", sentRobotsFromStone=" + sentRobotsFromStone +
                '}';
    }
}
