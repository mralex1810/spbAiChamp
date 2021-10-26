package spb_ai_champ.myClasses;

import spb_ai_champ.model.Resource;

public class MoveEvent implements Comparable<MoveEvent> {

    private final int tick;
    private final int planet;
    private final int endPlanet;
    private final Resource resource;
    private final int robots;

    public MoveEvent(int tick, int planet, int endPlanet, Resource resource, int robots) {
        this.tick = tick;
        this.planet = planet;
        this.endPlanet = endPlanet;
        this.resource = resource;
        this.robots = robots;

    }

    public MoveEvent(MoveEvent moveEvent, int delay, int planet, int robots) {
        tick = moveEvent.getTick() + delay;
        this.planet = planet;
        this.robots = robots;
        resource = moveEvent.getResource();
        endPlanet = moveEvent.getEndPlanet();
    }

    public int getEndPlanet() {
        return endPlanet;
    }

    public Resource getResource() {
        return resource;
    }

    public int getTick() {
        return tick;
    }

    public int getPlanet() {
        return planet;
    }

    public int getRobots() {
        return robots;
    }

    @Override
    public String toString(){
        return "{tick: " + tick + " planet: " + planet +
                " endPlanet: " + endPlanet + " Resource: " + resource + " robots: " + robots + "}";
    }

    @Override
    public int compareTo(MoveEvent o) {
        if (tick == o.getTick()) {
            if (planet < o.getPlanet()) return -1;
            else if (planet > o.getPlanet()) return 1;
            return 0;
        } else if (tick < o.getTick()) return -1;
        return 1;
    }
}
