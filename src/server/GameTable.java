import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

enum ShotResult {
    Hit,
    Miss,
    Destroyed,
    GameOver
}

class Boat {
    public int size;
    public int[] pos;
    public byte dir;

    Boat() {
        this.size = 0;
        this.dir = 0;
        this.pos = null;
    }

    Boat(int size, int base_pos, byte dir, int side) {
        this.size = size;
        this.dir = dir;
        this.pos = new int[size];

        if (dir == 0b1000) {
            for (int i = 0; i < pos.length; i++) {
                pos[i] = base_pos + i;
            }
        } else if (dir == 0b0100) {
            for (int i = 0; i < pos.length; i++) {
                pos[i] = base_pos - i;
            }
        } else if (dir == 0b0010) {
            for (int i = 0; i < pos.length; i++) {
                pos[i] = base_pos + (i*side);
            }
        } else if (dir == 0b0001) {
            for (int i = 0; i < pos.length; i++) {
                pos[i] = base_pos - (i*side);
            }
        }
    }

    public void print() {
        System.out.print("(");
        for (int i = 0; i < pos.length-1; i++) {
            System.out.print(pos[i] + ",");
        }
        System.out.println(pos[pos.length-1] + ")");
    }
}

public class GameTable {

    private Boolean[] table;
    private int size;
    private int side;
    private int boat_count;
    private int[] boat_sizes = {2, 3, 4};
    private List<Boat> boats;


    GameTable() {
        this.size = 100;
        this.table =  new Boolean[size];
        this.side = 10; // (int)((double)this.size/10.0);
        this.boat_count = boat_sizes.length * 2;
        this.boats = new ArrayList<>(boat_count);
        this.generate();
    }

    public void print() {

        if (table[0] == null) {
            System.out.print("[ ] ");
        } else if (!table[0]) {
            System.out.print("[o] ");
        } else {
            System.out.print("[x] ");
        }

        for (int i = 1; i < size; i++) {
            if (i % side == 0) {
                System.out.println("");
            }
            if (table[i] == null) {
                System.out.print("[ ] ");
            } else if (!table[i]) {
                System.out.print("[o] ");
            } else {
                System.out.print("[x] ");
            }
        }
        System.out.print("\n\n\n");
    }

    private byte posCheck(int pos, int boat_size) {
        byte res = 0b0000;

        if (table[pos] == null) {

            int col = pos % side;   
            int row = (int)((double)pos/(double)side);

            int i = 0;
            if ( (col + boat_size) < 10) { // Right
                while (i < boat_size && table[i + pos] == null) {
                    i++;
                }
                if (i == boat_size) {
                    res |= 0b1000;
                }
            }
            i = 0;
            if ( (col - boat_size) >= 0) { // Left
                while (i < boat_size && table[pos - i] == null) {
                    i++;
                }
                if (i == boat_size) {
                    res |= 0b0100;
                }
            }
            i = 0;
            if ( (row + boat_size) < 10) { // Up
                while (i < boat_size && table[(i*side) + pos] == null) {
                    i++;
                }
                if (i == boat_size) {
                    res |= 0b0010;
                }
            }
            i = 0;
            if ( (row - boat_size) >= 0) { // Down
                while (i < boat_size && table[pos - (i*side)] == null) {
                    i++;
                }
                if (i == boat_size) {
                    res |= 0b0001;
                }
            }
        }
        return res;
    }

    private void addRight(int i, int pos) {
        for (int k = 0; k < boat_sizes[i]; k++) {
            table[k+pos] = true;
        }
    }
    
    private void addLeft(int i, int pos) {
        for (int k = 0; k < boat_sizes[i]; k++) {
            table[pos-k] = true;
        }
    }

    private void addUp(int i, int pos) {
        for (int k = 0; k < boat_sizes[i]; k++) {
            table[pos+(k*side)] = true;
        }
    }

    private void addDown(int i, int pos) {
        for (int k = 0; k < boat_sizes[i]; k++) {
            table[pos-(k*side)] = true;
        }
    }

    private void generate () {
        Random r = new Random();

        for (int i = 0; i < boat_sizes.length; i++) {
            for (int j = 0; j < 2; j++) {

                int pos = r.nextInt(size);  // [0 - 99]
                byte dir = posCheck(pos, boat_sizes[i]);

                if (dir != 0) {
                    Byte [] dirs = {0b0001, 0b0010, 0b0100, 0b1000};

                    List<Byte> l = Arrays.asList(dirs);
                    Collections.shuffle(l);

                    dirs = l.toArray(new Byte[0]);

                    int k = 0;
                    boolean placed = false;

                    while (k < 4 && !placed) {
                        if ( (dir & dirs[k]) != 0) {
                            if ( (dirs[k] & 0b1000) != 0 ) { // right
                                addRight(i, pos);
                            } else if ( (dirs[k] & 0b0100) != 0 ) { // left
                                addLeft(i, pos);
                            } else if ( (dirs[k] & 0b0010) != 0) { // up
                                addUp(i, pos);
                            } else if ( (dirs[k] & 0b0001) != 0) { // down
                                addDown(i, pos);
                            }

                            placed = true;
                            boats.add(new Boat(boat_sizes[i], pos, dirs[k], side));
                        }
                        k++;
                    }

                } else {
                    j--;
                }
            }
        }

        int c = 0;
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null && table[i]) {
                c++;
            }
        }

        if (c != 18) {  // Generation fail safe
            System.out.println("Bomba atomica paia foda");
            this.size = 100;
            this.table =  new Boolean[size];
            this.side = 10; // (int)((double)this.size/10.0);
            this.boat_count = boat_sizes.length * 2;
            this.boats = new ArrayList<>(boat_count);
            this.generate();
        }
    }

    private boolean boatIsAlive(Boat b) {
        boolean res = false;
    
        int c = 0;
        for (int i = 0; i < b.pos.length; i++) {
            if (table[b.pos[i]]) {
                c++;
            }
        }
        if (c > 0) {
            res = true;
        }
        return res;
    }

    public ShotResult shot(int x, int y) {
        int pos = (10*x) + y;
        return shot(pos);
    }

    public ShotResult shot(int pos) {

        ShotResult res = ShotResult.Miss;

        Boat[] arr = boats.toArray(new Boat[0]);

        if (table[pos] != null && table[pos] == true) {
            for (int i = 0; i < arr.length; i++) {
                Boat b = arr[i];

                boolean status = false;
                int j = 0;
                while (j < b.pos.length && !status) {
                    if (pos == b.pos[j]) {
                        status = true;
                    }
                    j++;
                }

                if (status) {
                    b.print();
                    table[pos] = false;
                    if (boatIsAlive(b)) {
                        res = ShotResult.Hit;
                    } else {
                        res = ShotResult.Destroyed;
                        this.boat_count--;
                        if (boat_count == 0) {
                            res = ShotResult.GameOver;
                        }
                    }
                    i = arr.length;
                }
            }
        }
        return res;
    }

    public static void main(String[] args) {
        GameTable table = new GameTable();
        table.generate();
        table.print();
    
        Scanner s = new Scanner(System.in);

        ShotResult r;
        do {
            System.out.print("Pos: ");
            int pos = s.nextInt();

            if (pos < 0 || pos >= 100) {
                System.out.println("nonono");
            }

            r = table.shot(pos);

            System.out.println("\n" + r);
            table.print();

        } while (r != ShotResult.GameOver);

        s.close();
    }

}
