package spb_ai_champ.model;

public class Event {

    private final int tick;
    private final int planet;
    private final int circle;

    private final int robots;

    public Event(int tick, int planet, int circle, int robots) {
        this.tick = tick;
        this.planet = planet;
        this.circle = circle;
        this.robots = robots;

    }

    public Event(Event event, int delay) {
        tick = event.getTick() + delay;
        planet = event.getPlanet();
        circle = event.getCircle();
        robots = event.getRobots();
    }

    public Event(Event event, int delay, int robots) {
        tick = event.getTick() + delay;
        planet = event.getPlanet();
        circle = event.getCircle();
        this.robots = robots;
    }

    public int getCircle() {
        return circle;
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
