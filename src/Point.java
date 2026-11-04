import java.util.List;

public class Point {
    private int x;
    private double y;
    List<Integer> rs;

    Point(int x, double y, List<Integer> rs){
        this.x = x;
        this.y = y;
        this.rs = rs;
    }

    public int getX(){
        return this.x;
    }

    public double getY(){
        return this.y;
    }

    public List<Integer> getRs(){
        return this.rs;
    }
}
