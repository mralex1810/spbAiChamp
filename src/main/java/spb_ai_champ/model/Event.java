package spb_ai_champ.model;

public class Event {

    private final int tick;
    private final int planet;
    private final int endPlanet;
    private final Resource resource;
    private final int robots;

    public Event(int tick, int planet, int endPlanet, Resource resource, int robots) {
        this.tick = tick;
        this.planet = planet;
        this.endPlanet = endPlanet;
        this.resource = resource;
        this.robots = robots;

    }

    public Event(Event event, int delay, int planet, int robots) {
        tick = event.getTick() + delay;
        this.planet = planet;
        this.robots = robots;
        resource = event.getResource();
        endPlanet = event.getEndPlanet();
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
}
