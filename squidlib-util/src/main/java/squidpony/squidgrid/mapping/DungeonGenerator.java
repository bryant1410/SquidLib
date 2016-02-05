package squidpony.squidgrid.mapping;

import squidpony.annotation.Beta;
import squidpony.squidai.DijkstraMap;
import squidpony.squidgrid.MultiSpill;
import squidpony.squidgrid.Spill;
import squidpony.squidgrid.mapping.styled.DungeonBoneGen;
import squidpony.squidgrid.mapping.styled.TilesetType;
import squidpony.squidmath.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static squidpony.squidmath.CoordPacker.*;

/**
 * The primary way to create a more-complete dungeon, layering different effects and modifications on top of
 * a DungeonBoneGen's dungeon.
 *
 * The main technique for using this is simple: Construct a DungeonGenerator, usually with the desired width and height,
 * then call any feature adding methods that you want in the dungeon, like addWater(), addTraps, addGrass(), or
 * addDoors(). Some of these take different parameters, like addDoors() which need to know if it should check openings
 * that are two cells wide to add a door and a wall to, or whether it should only add doors to single-cell openings.
 * Then call generate() to get a char[][] with the desired dungeon map, using a fixed repertoire of chars to represent
 * the different features. After calling generate(), you can safely get the values from the stairsUp and stairsDown
 * fields, which are Coords that should be a long distance from each other but connected in the dungeon. You may want
 * to change those to staircase characters, but there's no requirement to do anything with them. The DungeonUtility
 * field of this class, utility, is a convenient way of accessing the non-static methods in that class, such as
 * randomFloor(), without needing to create another DungeonUtility (this class creates one, so you don't have to).
 *
 * @see squidpony.squidgrid.mapping.DungeonUtility
 *
 * @author Eben Howard - http://squidpony.com - howard@squidpony.com
 * @author Tommy Ettinger - https://github.com/tommyettinger
 */
@Beta
public class DungeonGenerator {

    /**
     * The effects that can be applied to this dungeon. More may be added in future releases.
     */
    public enum FillEffect
    {
        /**
         * Water, represented by '~'
         */
        WATER,
        /**
         * Doors, represented by '+' for east-to-west connections or '/' for north-to-south ones.
         */
        DOORS,
        /**
         * Traps, represented by '^'
         */
        TRAPS,
        /**
         * Grass, represented by '"'
         */
        GRASS,
        /**
         * Boulders strewn about open areas, represented by '#' and treated as walls
         */
        BOULDERS,
        /**
         * Islands of ground, '.', surrounded by shallow water, ',', to place in water at evenly spaced points
         */
        ISLANDS
    }

    /**
     * The effects that will be applied when generate is called. Strongly prefer using addWater, addDoors, addTraps,
     * and addGrass.
     */
    public EnumMap<FillEffect, Integer> fx;
    private DungeonBoneGen gen;
    public DungeonUtility utility;
    private int height, width;
    public Coord stairsUp = null, stairsDown = null;
    public StatefulRNG rng;
    private long rebuildSeed;
    private boolean seedFixed = false;

    private char[][] dungeon = null;

    /**
     * Get the most recently generated char[][] dungeon out of this class. The
     * dungeon may be null if generate() or setDungeon() have not been called.
     * @return a char[][] dungeon, or null.
     */
    public char[][] getDungeon() {
        return dungeon;
    }
    /**
     * Get the most recently generated char[][] dungeon out of this class without any chars other than '#' or '.', for
     * walls and floors respectively. The dungeon may be null if generate() or setDungeon() have not been called.
     * @return a char[][] dungeon with only '#' for walls and '.' for floors, or null.
     */
    public char[][] getBareDungeon() {
        return DungeonUtility.simplifyDungeon(dungeon);
    }

    /**
     * Change the underlying char[][]; only affects the toString method, and of course getDungeon.
     * @param dungeon a char[][], probably produced by an earlier call to this class and then modified.
     */
    public void setDungeon(char[][] dungeon) {
        this.dungeon = dungeon;
        if(dungeon == null)
        {
            width = 0;
            height = 0;
            return;
        }
        width = dungeon.length;
        if(width > 0)
            height = dungeon[0].length;
    }

    /**
     * Height of the dungeon in cells.
     * @return Height of the dungeon in cells.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Width of the dungeon in cells.
     * @return Width of the dungeon in cells.
     */
    public int getWidth() {
        return width;
    }


    /**
     * Make a DungeonGenerator with a LightRNG using a random seed, height 40, and width 40.
     */
    public DungeonGenerator()
    {
        rng = new StatefulRNG();
        gen = new DungeonBoneGen(rng);
        utility = new DungeonUtility(rng);
        rebuildSeed = rng.getState();
        height = 40;
        width = 40;
        fx = new EnumMap<>(FillEffect.class);
    }

    /**
     * Make a DungeonGenerator with the given height and width, and the RNG used for generating a dungeon and
     * adding features will be a LightRNG using a random seed.
     * @param width The width of the dungeon in cells.
     * @param height The height of the dungeon in cells.
     */
    public DungeonGenerator(int width, int height)
    {
    	this(width, height, new RNG(new LightRNG()));
    }

    /**
     * Make a DungeonGenerator with the given height, width, and RNG. Use this if you want to seed the RNG.
     * @param width The width of the dungeon in cells.
     * @param height The height of the dungeon in cells.
     * @param rng The RNG to use for all purposes in this class; if this has been seeded and you want the same
     *            results from map generation every time, don't use the same RNG object in other places.
     */
    public DungeonGenerator(int width, int height, RNG rng)
    {
        this.rng = (rng instanceof StatefulRNG) ? (StatefulRNG) rng : new StatefulRNG(rng.nextLong());
        gen = new DungeonBoneGen(this.rng);
        utility = new DungeonUtility(this.rng);
        rebuildSeed = this.rng.getState();
        this.height = height;
        this.width = width;
        fx = new EnumMap<>(FillEffect.class);
    }

    /**
     * Copies all fields from copying and makes a new DungeonGenerator.
     * @param copying
     */
    public DungeonGenerator(DungeonGenerator copying)
    {
        rng = new StatefulRNG(copying.rng.getState());
        gen = new DungeonBoneGen(rng);
        utility = new DungeonUtility(rng);
        rebuildSeed = rng.getState();
        height = copying.height;
        width = copying.width;
        fx = new EnumMap<>(copying.fx);
        dungeon = copying.dungeon;
    }

    /**
     * Turns the majority of the given percentage of floor cells into water cells, represented by '~'. Water will be
     * clustered into a random number of pools, with more appearing if needed to fill (two thirds of) the percentage.
     * Each pool will have randomized volume that should fill or get very close to filling two thirds of the requested
     * percentage, unless the pools encounter too much tight space. If this DungeonGenerator previously had addWater
     * called, the latest call will take precedence. No islands will be placed with this variant, but the edge of the
     * water will be shallow, represented by ','.
     * @param percentage the percentage of floor cells to fill with water; this can vary quite a lot. It is not
     *                   intended to allow filling very high (over 66%) percentages of map with water, though you can do
     *                   this by giving a percentage of between 100 and 150.
     * @return this DungeonGenerator; can be chained
     */
    public DungeonGenerator addWater(int percentage)
    {
        if(percentage < 0) percentage = 0;
        if(percentage > 150) percentage = 150;
        if(fx.containsKey(FillEffect.WATER)) fx.remove(FillEffect.WATER);
        fx.put(FillEffect.WATER, percentage);
        return this;
    }
    /**
     * Turns the majority of the given percentage of floor cells into water cells, represented by '~'. Water will be
     * clustered into a random number of pools, with more appearing if needed to fill the percentage. Each pool will
     * have randomized volume that should fill or get very close to filling (two thirds of) the requested percentage,
     * unless the pools encounter too much tight space. If this DungeonGenerator previously had addWater called, the
     * latest call will take precedence. If islandSpacing is greater than 1, then this will place islands of floor, '.',
     * surrounded by shallow water, ',', at about the specified distance with Euclidean measurement.
     * @param percentage the percentage of floor cells to fill with water; this can vary quite a lot. It may be
     *                   difficult to fill very high (over 66%) percentages of map with water, though you can do this by
     *                   giving a percentage of between 100 and 150.
     * @param islandSpacing if greater than 1, islands will be placed randomly this many cells apart.
     * @return this DungeonGenerator; can be chained
     */
    public DungeonGenerator addWater(int percentage, int islandSpacing)
    {
        if(percentage < 0) percentage = 0;
        if(percentage > 150) percentage = 150;
        if(fx.containsKey(FillEffect.WATER)) fx.remove(FillEffect.WATER);
        fx.put(FillEffect.WATER, percentage);
        if(fx.containsKey(FillEffect.ISLANDS)) fx.remove(FillEffect.ISLANDS);
        if(islandSpacing > 1)
            fx.put(FillEffect.ISLANDS, islandSpacing);
        return this;
    }

    /**
     * Turns the majority of the given percentage of floor cells into grass cells, represented by '"'. Grass will be
     * clustered into a random number of patches, with more appearing if needed to fill the percentage. Each area will
     * have randomized volume that should fill or get very close to filling (two thirds of) the requested percentage,
     * unless the patches encounter too much tight space. If this DungeonGenerator previously had addGrass called, the
     * latest call will take precedence.
     * @param percentage the percentage of floor cells to fill with grass; this can vary quite a lot. It may be
     *                   difficult to fill very high (over 66%) percentages of map with grass, though you can do this by
     *                   giving a percentage of between 100 and 150.
     * @return this DungeonGenerator; can be chained
     */
    public DungeonGenerator addGrass(int percentage)
    {
        if(percentage < 0) percentage = 0;
        if(percentage > 150) percentage = 150;
        if(fx.containsKey(FillEffect.GRASS)) fx.remove(FillEffect.GRASS);
        fx.put(FillEffect.GRASS, percentage);
        return this;
    }
    /**
     * Turns the given percentage of floor cells not already adjacent to walls into wall cells, represented by '#'.
     * If this DungeonGenerator previously had addBoulders called, the latest call will take precedence.
     * @param percentage the percentage of floor cells not adjacent to walls to fill with boulders.
     * @return this DungeonGenerator; can be chained
     */
    public DungeonGenerator addBoulders(int percentage)
    {
        if(percentage < 0) percentage = 0;
        if(percentage > 100) percentage = 100;
        if(fx.containsKey(FillEffect.BOULDERS)) fx.remove(FillEffect.BOULDERS);
        fx.put(FillEffect.BOULDERS, percentage);
        return this;
    }
    /**
     * Turns the given percentage of viable doorways into doors, represented by '+' for doors that allow travel along
     * the x-axis and '/' for doors that allow travel along the y-axis. If doubleDoors is true,
     * 2-cell-wide openings will be considered viable doorways and will fill one cell with a wall, the other a door.
     * If this DungeonGenerator previously had addDoors called, the latest call will take precedence.
     * @param percentage the percentage of valid openings to corridors to fill with doors; should be between 10 and
     *                   20 if you want doors to appear more than a few times, but not fill every possible opening.
     * @param doubleDoors true if you want two-cell-wide openings to receive a door and a wall; false if only
     *                    one-cell-wide openings should receive doors. Usually, this should be true.
     * @return this DungeonGenerator; can be chained
     */
    public DungeonGenerator addDoors(int percentage, boolean doubleDoors)
    {
        if(percentage < 0) percentage = 0;
        if(percentage > 100) percentage = 100;
        if(doubleDoors) percentage *= -1;
        if(fx.containsKey(FillEffect.DOORS)) fx.remove(FillEffect.DOORS);
        fx.put(FillEffect.DOORS, percentage);
        return this;
    }

    /**
     * Turns the given percentage of open area floor cells into trap cells, represented by '^'. Corridors that have no
     * possible way to move around a trap will not receive traps, ever. If this DungeonGenerator previously had
     * addTraps called, the latest call will take precedence.
     * @param percentage the percentage of valid cells to fill with traps; should be no higher than 5 unless
     *                   the dungeon floor is meant to be a kill screen or minefield.
     * @return this DungeonGenerator; can be chained
     */
    public DungeonGenerator addTraps(int percentage)
    {
        if(percentage < 0) percentage = 0;
        if(percentage > 100) percentage = 100;
        if(fx.containsKey(FillEffect.TRAPS)) fx.remove(FillEffect.TRAPS);
        fx.put(FillEffect.TRAPS, percentage);
        return this;
    }

    /**
     * Removes any door, water, or trap insertion effects that this DungeonGenerator would put in future dungeons.
     * @return this DungeonGenerator, with all effects removed. Can be chained.
     */
    public DungeonGenerator clearEffects()
    {
        fx.clear();
        return this;
    }

    private LinkedHashSet<Coord> removeAdjacent(LinkedHashSet<Coord> coll, Coord pt)
    {
        for(Coord temp : new Coord[]{Coord.get(pt.x + 1, pt.y), Coord.get(pt.x - 1, pt.y),
                Coord.get(pt.x, pt.y + 1), Coord.get(pt.x, pt.y - 1)})
        {
            if(coll.contains(temp) && !(temp.x == pt.x && temp.y == pt.y))
                coll.remove(temp);
        }

        return coll;
    }
    private LinkedHashSet<Coord> removeAdjacent(LinkedHashSet<Coord> coll, Coord pt1, Coord pt2)
    {

        for(Coord temp : new Coord[]{Coord.get(pt1.x + 1, pt1.y), Coord.get(pt1.x - 1, pt1.y),
                Coord.get(pt1.x, pt1.y + 1), Coord.get(pt1.x, pt1.y - 1),
                Coord.get(pt2.x + 1, pt2.y), Coord.get(pt2.x - 1, pt2.y),
                Coord.get(pt2.x, pt2.y + 1), Coord.get(pt2.x, pt2.y - 1),})
        {
            if(coll.contains(temp) && !(temp.x == pt1.x && temp.y == pt1.y) && !(temp.x == pt2.x && temp.y == pt2.y))
                coll.remove(temp);
        }

        return coll;
    }

    private LinkedHashSet<Coord> viableDoorways(boolean doubleDoors, char[][] map)
    {
        LinkedHashSet<Coord> doors = new LinkedHashSet<>();
        for(int x = 1; x < map.length - 1; x++) {
            for (int y = 1; y < map[x].length - 1; y++) {
                if(map[x][y] == '#')
                    continue;
                if (doubleDoors) {
                    if (x >= map.length - 2 || y >= map[x].length - 2)
                        continue;
                    else {
                        if (map[x+1][y] != '#' &&
                                map[x + 2][y] == '#' && map[x - 1][y] == '#'
                                && map[x][y + 1] != '#' && map[x][y - 1] != '#'
                                && map[x+1][y + 1] != '#' && map[x+1][y - 1] != '#') {
                            if (map[x + 2][y + 1] != '#' || map[x - 1][y + 1] != '#' || map[x + 2][y - 1] != '#' || map[x - 1][y - 1] != '#') {
                                doors.add(Coord.get(x, y));
                                doors.add(Coord.get(x + 1, y));
                                doors = removeAdjacent(doors, Coord.get(x, y), Coord.get(x + 1, y));
                                continue;
                            }
                        } else if (map[x][y+1] != '#' &&
                                map[x][y + 2] == '#' && map[x][y - 1] == '#'
                                && map[x + 1][y] != '#' && map[x - 1][y] != '#'
                                && map[x + 1][y+1] != '#' && map[x - 1][y+1] != '#') {
                            if (map[x + 1][y + 2] != '#' || map[x + 1][y - 1] != '#' || map[x - 1][y + 2] != '#' || map[x - 1][y - 1] != '#') {
                                doors.add(Coord.get(x, y));
                                doors.add(Coord.get(x, y + 1));
                                doors = removeAdjacent(doors, Coord.get(x, y), Coord.get(x, y + 1));
                                continue;
                            }
                        }
                    }
                }
                if (map[x + 1][y] == '#' && map[x - 1][y] == '#' && map[x][y + 1] != '#' && map[x][y - 1] != '#') {
                    if (map[x + 1][y + 1] != '#' || map[x - 1][y + 1] != '#' || map[x + 1][y - 1] != '#' || map[x - 1][y - 1] != '#') {
                        doors.add(Coord.get(x, y));
                        doors = removeAdjacent(doors, Coord.get(x, y));
                    }
                } else if (map[x][y + 1] == '#' && map[x][y - 1] == '#' && map[x + 1][y] != '#' && map[x - 1][y] != '#') {
                    if (map[x + 1][y + 1] != '#' || map[x + 1][y - 1] != '#' || map[x - 1][y + 1] != '#' || map[x - 1][y - 1] != '#') {
                        doors.add(Coord.get(x, y));
                        doors = removeAdjacent(doors, Coord.get(x, y));
                    }
                }

            }
        }


        return doors;
    }

    /**
     * Generate a char[][] dungeon using TilesetType.DEFAULT_DUNGEON; this produces a dungeon appropriate for a level
     * of ruins or a partially constructed dungeon. This uses '#' for walls, '.' for floors, '~' for deep water, ',' for
     * shallow water, '^' for traps, '+' for doors that provide horizontal passage, and '/' for doors that provide
     * vertical passage. Use the addDoors, addWater, addGrass, and addTraps methods of this class to request these in
     * the generated map.
     * Also sets the fields stairsUp and stairsDown to two randomly chosen, distant, connected, walkable cells.
     * @return a char[][] dungeon
     */
    public char[][] generate() {
        return generate(TilesetType.DEFAULT_DUNGEON);
    }

    /**
     * Generate a char[][] dungeon given a TilesetType; the comments in that class provide some opinions on what
     * each TilesetType value could be used for in a game. This uses '#' for walls, '.' for floors, '~' for deep water,
     * ',' for shallow water, '^' for traps, '+' for doors that provide horizontal passage, and '/' for doors that
     * provide vertical passage. Use the addDoors, addWater, addGrass, and addTraps methods of this class to request
     * these in the generated map.
     * Also sets the fields stairsUp and stairsDown to two randomly chosen, distant, connected, walkable cells.
     * @see squidpony.squidgrid.mapping.styled.TilesetType
     * @param kind a TilesetType enum value, such as TilesetType.DEFAULT_DUNGEON
     * @return a char[][] dungeon
     */
    public char[][] generate(TilesetType kind)
    {
        seedFixed = true;
        rebuildSeed = rng.getState();
        return generate(gen.generate(kind, width, height));
    }

    /**
     * Generate a char[][] dungeon with extra features given a baseDungeon that has already been generated.
     * Typically, you want to call generate with a TilesetType or no argument for the easiest generation; this method
     * is meant for adding features like water and doors to existing simple maps.
     * This uses '#' for walls, '.' for floors, '~' for deep water, ',' for shallow water, '^' for traps, '+' for doors
     * that provide horizontal passage, and '/' for doors that provide vertical passage.
     * Use the addDoors, addWater, addGrass, and addTraps methods of this class to request these in the generated map.
     * Also sets the fields stairsUp and stairsDown to two randomly chosen, distant, connected, walkable cells.
     * @param baseDungeon a pre-made dungeon consisting of '#' for walls and '.' for floors
     * @return a char[][] dungeon
     */
    public char[][] generate(char[][] baseDungeon)
    {
        if(!seedFixed)
        {
            rebuildSeed = rng.getState();
        }
        seedFixed = false;
        char[][] map = DungeonBoneGen.wallWrap(baseDungeon);
        DijkstraMap dijkstra = new DijkstraMap(map);
        int frustrated = 0;
        do {
            dijkstra.clearGoals();
            stairsUp = utility.randomFloor(map);
            dijkstra.setGoal(stairsUp);
            dijkstra.scan(null);
            frustrated++;
        }while (dijkstra.getMappedCount() < width + height && frustrated < 15);
        double maxDijkstra = 0.0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if(dijkstra.gradientMap[i][j] >= DijkstraMap.FLOOR) {
                    map[i][j] = '#';
                }
                else if(dijkstra.gradientMap[i][j] > maxDijkstra) {
                    maxDijkstra = dijkstra.gradientMap[i][j];
                }
            }
        }
        int wmod = rng.nextInt(width), hmod = rng.nextInt(height);
        OUTER_LOOP:
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (dijkstra.gradientMap[(i + wmod) % width][(j + hmod) % height] < DijkstraMap.FLOOR &&
                        dijkstra.gradientMap[(i + wmod) % width][(j + hmod) % height] > maxDijkstra * 0.7) {
                    stairsDown = Coord.get((i + wmod) % width, (j + hmod) % height);
                    break OUTER_LOOP;
                }
            }
        }
        return innerGenerate(map);
    }

    /**
     * Generate a char[][] dungeon with extra features given a baseDungeon that has already been generated, and that
     * already has staircases represented by greater than and less than signs.
     * Typically, you want to call generate with a TilesetType or no argument for the easiest generation; this method
     * is meant for adding features like water and doors to existing simple maps.
     * This uses '#' for walls, '.' for floors, '~' for deep water, ',' for shallow water, '^' for traps, '+' for doors
     * that provide horizontal passage, and '/' for doors that provide vertical passage.
     * Use the addDoors, addWater, addGrass, and addTraps methods of this class to request these in the generated map.
     * Also sets the fields stairsUp and stairsDown to null, and expects stairs to be already handled.
     * @param baseDungeon a pre-made dungeon consisting of '#' for walls and '.' for floors, with stairs already in
     * @return a char[][] dungeon
     */
    public char[][] generateRespectingStairs(char[][] baseDungeon) {
        if(!seedFixed)
        {
            rebuildSeed = rng.getState();
        }
        seedFixed = false;
        char[][] map = DungeonBoneGen.wallWrap(baseDungeon);
        DijkstraMap dijkstra = new DijkstraMap(map);
        stairsUp = null;
        stairsDown = null;

        dijkstra.clearGoals();
        Coord[] stairs = allPacked(unionPacked(pack(map, '<'), pack(map, '>')));
        for (Coord s : stairs) {
            dijkstra.setGoal(s);
        }
        dijkstra.scan(null);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (dijkstra.gradientMap[i][j] >= DijkstraMap.FLOOR) {
                    map[i][j] = '#';
                }
            }
        }
        return innerGenerate(map);
    }




    private char[][] innerGenerate(char[][] map)
    {

        LinkedHashSet<Coord> floors = new LinkedHashSet<>();
        LinkedHashSet<Coord> doorways;
        LinkedHashSet<Coord> hazards = new LinkedHashSet<>();
        Coord temp;
        boolean doubleDoors = false;
        int doorFill = 0;
        int waterFill = 0;
        int grassFill = 0;
        int trapFill = 0;
        double boulderFill = 0.0;
        int islandSpacing = 0;
        if(fx.containsKey(FillEffect.DOORS))
        {
            doorFill = fx.get(FillEffect.DOORS);
            if(doorFill < 0)
            {
                doubleDoors = true;
                doorFill *= -1;
            }
        }
        if(fx.containsKey(FillEffect.GRASS)) {
            grassFill = fx.get(FillEffect.GRASS);
        }
        if(fx.containsKey(FillEffect.WATER)) {
            waterFill = fx.get(FillEffect.WATER);
        }
        if(fx.containsKey(FillEffect.BOULDERS)) {
            boulderFill = fx.get(FillEffect.BOULDERS) * 0.01;
        }
        if(fx.containsKey(FillEffect.ISLANDS)) {
            islandSpacing = fx.get(FillEffect.ISLANDS);
        }
        if(fx.containsKey(FillEffect.TRAPS)) {
            trapFill = fx.get(FillEffect.TRAPS);
        }

        double floorRate = 1.0, waterRate = waterFill / 150.0, grassRate = grassFill / 150.0;
        if(waterRate + grassRate > 1.0)
        {
            waterRate /= (waterFill + grassFill) / 100.0;
            grassRate /= (waterFill + grassFill) / 100.0;
        }
        floorRate -= waterRate + grassRate;
        doorways = viableDoorways(doubleDoors, map);

        LinkedHashSet<Coord> obstacles = new LinkedHashSet<>(doorways.size() * doorFill / 100);
        if(doorFill > 0)
        {
            int total = doorways.size() * doorFill / 100;

            BigLoop:
            for(int i = 0; i < total; i++)
            {
                Coord entry = doorways.toArray(new Coord[doorways.size()])[rng.nextInt(doorways.size())];
                if(map[entry.x][entry.y] == '<' || map[entry.x][entry.y] == '>')
                    continue;
                if(map[entry.x - 1][entry.y] != '#' && map[entry.x + 1][entry.y] != '#')
                {
                    map[entry.x][entry.y] = '+';
                }
                else {
                    map[entry.x][entry.y] = '/';
                }
                obstacles.add(entry);
                Coord[] adj = new Coord[]{Coord.get(entry.x + 1, entry.y), Coord.get(entry.x - 1, entry.y),
                        Coord.get(entry.x, entry.y + 1), Coord.get(entry.x, entry.y - 1)};
                for(Coord near : adj) {
                    if (doorways.contains(near)) {
                        map[near.x][near.y] = '#';
                        obstacles.add(near);
                        doorways.remove(near);
                        i++;
                        doorways.remove(entry);
                        continue BigLoop;
                    }
                }
                doorways.remove(entry);
            }
        }
        if (boulderFill > 0.0) {
            short[] walls = unionPacked(unionPacked(pack(map, '>'), pack(map, '<')), pack(map, '#'));
            short[] more = expand(walls, 1, width, height);
            short[] rect = rectangle(width, height);
            short[] viable = differencePacked(rect, more);
            Coord[] boulders = randomSample(viable, boulderFill, rng);
            for (int i = 0; i < boulders.length; i++) {
                map[boulders[i].x][boulders[i].y] = '#';
            }
        }


        if(trapFill > 0) {
            for (int x = 1; x < map.length - 1; x++) {
                for (int y = 1; y < map[x].length - 1; y++) {
                    temp = Coord.get(x, y);
                    if (map[x][y] == '.' && !obstacles.contains(temp)) {
                        floors.add(Coord.get(x, y));
                        int ctr = 0;
                        if (map[x + 1][y] != '#') ++ctr;
                        if (map[x - 1][y] != '#') ++ctr;
                        if (map[x][y + 1] != '#') ++ctr;
                        if (map[x][y - 1] != '#') ++ctr;
                        if (map[x + 1][y + 1] != '#') ++ctr;
                        if (map[x - 1][y + 1] != '#') ++ctr;
                        if (map[x + 1][y - 1] != '#') ++ctr;
                        if (map[x - 1][y - 1] != '#') ++ctr;
                        if (ctr >= 5) hazards.add(Coord.get(x, y));
                    }
                }
            }
        }

        MultiSpill ms = new MultiSpill(map, Spill.Measurement.MANHATTAN, rng);
        LinkedHashMap<Coord, Double> fillers = new LinkedHashMap<>(128);
        ArrayList<Coord> dots = PoissonDisk.sampleMap(map, Math.min(width, height) / 8f, rng, '#', '+', '/','<', '>');
        for (int i = 0; i < dots.size(); i++) {
            switch (i % 3) {
                case 0:
                    fillers.put(dots.get(i), floorRate);
                    break;
                case 1:
                    fillers.put(dots.get(i), waterRate);
                    break;
                case 2:
                    fillers.put(dots.get(i), grassRate);
                    break;
            }
        }

        ArrayList<ArrayList<Coord>> fills = ms.start(fillers, -1, null);

        int ctr = 0;
        for(ArrayList<Coord> pts : fills)
        {
            switch (ctr % 3) {
                case 0:
                    break;
                case 1:
                    floors.removeAll(pts);
                    hazards.removeAll(pts);
                    obstacles.addAll(pts);
                    for (int x = 1; x < width - 1; x++) {
                        for (int y = 1; y < height - 1; y++) {
                            if (ms.spillMap[x][y] == ctr && map[x][y] != '<' && map[x][y] != '>') {
                                map[x][y] = '~';
                            }
                        }
                    }
                    for (int x = 1; x < width - 1; x++) {
                        for (int y = 1; y < height - 1; y++) {
                            if (ms.spillMap[x][y] == ctr) {
                                if(map[x][y] != '<' && map[x][y] != '>' &&
                                        (map[x - 1][y] == '.' || map[x + 1][y] == '.' ||
                                        map[x][y - 1] == '.' || map[x][y + 1] == '.'))
                                    map[x][y] = ',';
                            }
                        }
                    }
                    if(islandSpacing > 1) {
                        ArrayList<Coord> islands = PoissonDisk.sampleMap(map, 1f * islandSpacing, rng, '#', '.', '"', '+', '/', '^', '<', '>');
                        for (Coord c : islands) {
                            map[c.x][c.y] = '.';
                            if (map[c.x - 1][c.y] != '#' && map[c.x - 1][c.y] != '<' && map[c.x - 1][c.y] != '>')
                                map[c.x - 1][c.y] = ',';
                            if (map[c.x + 1][c.y] != '#' && map[c.x + 1][c.y] != '<' && map[c.x + 1][c.y] != '>')
                                map[c.x + 1][c.y] = ',';
                            if (map[c.x][c.y-1] != '#' && map[c.x][c.y-1] != '<' && map[c.x][c.y-1] != '>')
                                map[c.x][c.y - 1] = ',';
                            if (map[c.x][c.y+1] != '#' && map[c.x][c.y+1] != '<' && map[c.x][c.y+1] != '>')
                                map[c.x][c.y + 1] = ',';
                        }
                    }

                    break;
                case 2:
                    floors.removeAll(pts);
                    hazards.removeAll(pts);
                    obstacles.addAll(pts);
                    for (int x = 1; x < width - 1; x++) {
                        for (int y = 1; y < height - 1; y++) {
                            if (ms.spillMap[x][y] == ctr && map[x][y] == '.') {
                                map[x][y] = '"';
                            }
                        }
                    }
                    break;
            }
            ctr++;
        }

        if(trapFill > 0)
        {
            int total = hazards.size() * trapFill / 100;

            for(int i = 0; i < total; i++)
            {
                Coord entry = hazards.toArray(new Coord[hazards.size()])[rng.nextInt(hazards.size())];
                if(map[entry.x][entry.y] == '<' || map[entry.x][entry.y] == '<')
                    continue;
                map[entry.x][entry.y] = '^';
                hazards.remove(entry);
            }
        }

        dungeon = map;
        return map;

    }

    @SuppressWarnings("unused")
	private char[][] innerGenerateOld(char[][] map)
    {

        LinkedHashSet<Coord> floors = new LinkedHashSet<>();
        LinkedHashSet<Coord> doorways;
        LinkedHashSet<Coord> hazards = new LinkedHashSet<>();
        Coord temp;
        boolean doubleDoors = false;
        int doorFill = 0;
        int waterFill = 0;
        int grassFill = 0;
        int trapFill = 0;
        int islandSpacing = 0;
        if(fx.containsKey(FillEffect.DOORS))
        {
            doorFill = fx.get(FillEffect.DOORS);
            if(doorFill < 0)
            {
                doubleDoors = true;
                doorFill *= -1;
            }
        }
        if(fx.containsKey(FillEffect.GRASS)) {
            grassFill = fx.get(FillEffect.GRASS);
        }
        if(fx.containsKey(FillEffect.WATER)) {
            waterFill = fx.get(FillEffect.WATER);
        }
        if(fx.containsKey(FillEffect.ISLANDS)) {
            islandSpacing = fx.get(FillEffect.ISLANDS);
        }
        if(fx.containsKey(FillEffect.TRAPS)) {
            trapFill = fx.get(FillEffect.TRAPS);
        }

        doorways = viableDoorways(doubleDoors, map);

        LinkedHashSet<Coord> obstacles = new LinkedHashSet<>(doorways.size() * doorFill / 100);
        if(doorFill > 0)
        {
            int total = doorways.size() * doorFill / 100;

            BigLoop:
            for(int i = 0; i < total; i++)
            {
                Coord entry = doorways.toArray(new Coord[doorways.size()])[rng.nextInt(doorways.size())];
                if(map[entry.x - 1][entry.y] != '#' && map[entry.x + 1][entry.y] != '#')
                {
                    map[entry.x][entry.y] = '+';
                }
                else {
                    map[entry.x][entry.y] = '/';
                }
                obstacles.add(entry);
                Coord[] adj = new Coord[]{Coord.get(entry.x + 1, entry.y), Coord.get(entry.x - 1, entry.y),
                        Coord.get(entry.x, entry.y + 1), Coord.get(entry.x, entry.y - 1)};
                for(Coord near : adj) {
                    if (doorways.contains(near)) {
                        map[near.x][near.y] = '#';
                        obstacles.add(Coord.get(near.x, near.y));
                        doorways.remove(near);
                        i++;
                        doorways.remove(entry);
                        continue BigLoop;
                    }
                }
                doorways.remove(entry);
            }
        }

        for(int x = 1; x < map.length - 1; x++)
        {
            for(int y = 1; y < map[x].length - 1; y++)
            {
                temp = Coord.get(x, y);
                if(map[x][y] == '.' && !obstacles.contains(temp))
                {
                    floors.add(Coord.get(x, y));
                    int ctr = 0;
                    if(map[x+1][y] != '#') ++ctr;
                    if(map[x-1][y] != '#') ++ctr;
                    if(map[x][y+1] != '#') ++ctr;
                    if(map[x][y-1] != '#') ++ctr;
                    if(map[x+1][y+1] != '#') ++ctr;
                    if(map[x-1][y+1] != '#') ++ctr;
                    if(map[x+1][y-1] != '#') ++ctr;
                    if(map[x-1][y-1] != '#') ++ctr;
                    if(ctr >= 5) hazards.add(Coord.get(x, y));
                }
            }
        }
        if(grassFill > 0)
        {
            int numPatches = rng.nextInt(8) + 2 + grassFill / 20;
            int[] volumes = new int[numPatches];
            int total = floors.size() * grassFill / 100;
            int error = 0;
            for(int i = 0; i < numPatches; i++) {
                volumes[i] = rng.nextInt(total / numPatches);
                error += volumes[i];
            }
            while (error > 0)
            {
                int n = rng.nextInt(total - error + 1);
                volumes[rng.nextInt(volumes.length)] += n;
                error -= n;
            }
            //volumes[0] += total - error;
            /*
            for(int i = 0; i < numPatches; i++) {
                int r = rng.nextInt(volumes[i] / 2) - volumes[i] / 4;
                volumes[i] += r;
                volumes[(i + 1) % numPatches] -= r;
            }
            */
            Spill spill = new Spill(map, Spill.Measurement.EUCLIDEAN, rng);
            int bonusVolume = 0;
            for(int i = 0; i < numPatches; i++)
            {
                floors.removeAll(obstacles);
                Coord entry = floors.toArray(new Coord[floors.size()])[rng.nextInt(floors.size())];
                spill.start(entry, volumes[i] / 3, obstacles);
                spill.start(entry, 2 * volumes[i] / 3, obstacles);
                ArrayList<Coord> ordered = new ArrayList<>(spill.start(entry, volumes[i], obstacles));
                floors.removeAll(ordered);
                hazards.removeAll(ordered);
                obstacles.addAll(ordered);

                if(spill.filled <= volumes[i])
                {
                    bonusVolume += volumes[i] - spill.filled;
                }

            }
            for(int x = 1; x < map.length - 1; x++) {
                for (int y = 1; y < map[x].length - 1; y++) {
                    if(spill.spillMap[x][y])
                        map[x][y] = '"';
                }
            }
            int frustration = 0;
            while (bonusVolume > 0 && frustration < 50)
            {
                Coord entry = utility.randomFloor(map);
                ArrayList<Coord> finisher = spill.start(entry, bonusVolume, obstacles);
                for(Coord p : finisher)
                {
                    map[p.x][p.y] = '"';
                }
                bonusVolume -= spill.filled;
                hazards.removeAll(finisher);
                frustration++;
            }
        }

        if(waterFill > 0)
        {
            int numPools = rng.nextInt(4) + 2 + waterFill / 20;
            int[] volumes = new int[numPools];
            int total = floors.size() * waterFill / 100;
            int error = 0;
            for(int i = 0; i < numPools; i++) {
                volumes[i] = total / numPools;
                error += volumes[i];
            }
            volumes[0] += total - error;

            for(int i = 0; i < numPools; i++) {
                int r = rng.nextInt(volumes[i] / 2) - volumes[i] / 4;
                volumes[i] += r;
                volumes[(i + 1) % numPools] -= r;
            }
            Spill spill = new Spill(map, Spill.Measurement.MANHATTAN, rng);
            int bonusVolume = 0;
            for(int i = 0; i < numPools; i++)
            {
                floors.removeAll(obstacles);
                Coord entry = floors.toArray(new Coord[floors.size()])[rng.nextInt(floors.size())];
//                spill.start(entry, volumes[i] / 3, obstacles);
//                spill.start(entry, 2 * volumes[i] / 3, obstacles);
                ArrayList<Coord> ordered = new ArrayList<>(spill.start(entry, volumes[i], obstacles));
                floors.removeAll(ordered);
                hazards.removeAll(ordered);
                obstacles.addAll(ordered);

                if(spill.filled <= volumes[i])
                {
                    bonusVolume += volumes[i] - spill.filled;
                }

            }
            for(int x = 1; x < map.length - 1; x++) {
                for (int y = 1; y < map[x].length - 1; y++) {
                    if (spill.spillMap[x][y]) {
                        map[x][y] = '~';
                    }
                }
            }
            int frustration = 0;
            while (bonusVolume > 0 && frustration < 50)
            {
                Coord entry = utility.randomFloor(map);
                ArrayList<Coord> finisher = spill.start(entry, bonusVolume, obstacles);
                for(Coord p : finisher)
                {
                    map[p.x][p.y] = '~';
                }
                bonusVolume -= spill.filled;
                hazards.removeAll(finisher);
                frustration++;
            }
            for(int x = 1; x < map.length - 1; x++) {
                for (int y = 1; y < map[x].length - 1; y++) {
                    if (spill.spillMap[x][y]) {
                        if(map[x - 1][y] == '.' || map[x + 1][y] == '.' ||
                                map[x][y - 1] == '.' || map[x][y + 1] == '.')
                            map[x][y] = ',';
                    }
                }
            }
            if(islandSpacing > 1) {
                ArrayList<Coord> islands = PoissonDisk.sampleMap(map, 1f * islandSpacing, rng, '#', '.', '"', '+', '/', '^');
                for (Coord c : islands) {
                    map[c.x][c.y] = '.';
                    if (map[c.x - 1][c.y] != '#')
                        map[c.x - 1][c.y] = ',';
                    if (map[c.x + 1][c.y] != '#')
                        map[c.x + 1][c.y] = ',';
                    if (map[c.x][c.y - 1] != '#')
                        map[c.x][c.y - 1] = ',';
                    if (map[c.x][c.y + 1] != '#')
                        map[c.x][c.y + 1] = ',';
                }
            }
        }



        if(trapFill > 0)
        {
            int total = hazards.size() * trapFill / 100;

            for(int i = 0; i < total; i++)
            {
                Coord entry = hazards.toArray(new Coord[hazards.size()])[rng.nextInt(hazards.size())];
                map[entry.x][entry.y] = '^';
                hazards.remove(entry);
            }
        }

        dungeon = map;
        return map;

    }

    /**
     * Gets the seed that can be used to rebuild an identical dungeon to the latest one generated (or the seed that
     * will be used to generate the first dungeon if none has been made yet). You can pass the long this returns to
     * the setState() method on this class' rng field, which assuming all other calls to generate a dungeon are
     * identical, will ensure generate() or generateRespectingStairs() will produce the same dungeon output as the
     * dungeon originally generated with the seed this returned.
     * <br>
     * You can also call getState() on the rng field yourself immediately before generating a dungeon, but this method
     * handles some complexities of when the state is actually used to generate a dungeon; since StatefulRNG objects can
     * be shared between different classes that use random numbers, the state could change between when you call
     * getState() and when this class generates a dungeon. Using getRebuildSeed() eliminates that confusion.
     * @return a seed as a long that can be passed to setState() on this class' rng field to recreate a dungeon
     */
    public long getRebuildSeed() {
        return rebuildSeed;
    }

    /**
     * Provides a string representation of the latest generated dungeon.
     *
     * @return a printable string version of the latest generated dungeon.
     */
    @Override
	public String toString() {
        char[][] trans = new char[height][width];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                trans[y][x] = dungeon[x][y];
            }
        }
        StringBuffer sb = new StringBuffer();
        for (int row = 0; row < height; row++) {
            sb.append(trans[row]);
            sb.append('\n');
        }
        return sb.toString();
    }

}
