package com.example.demo.utils;

import java.util.*;
//迷宫生成工具类
public class MazeGenerator {
    private static final int WALL = 1;
    private static final int PATH = 0;
    /**
     * 根据算法类型生成迷宫二维数组
     * @param rows 行数（必须为奇数）
     * @param cols 列数（必须为奇数）
     * @param algorithm dfs / prim / recursive
     * @return PATH=0, WALL=1 的二维数组
     */
    public static int[][] generate(int rows, int cols, String algorithm) {
        if (rows % 2 == 0) rows++;
        if (cols % 2 == 0) cols++;

        int[][] maze = new int[rows][cols];
        switch (algorithm.toLowerCase()) {
            case "dfs":
                generateDFS(maze);
                break;
            case "prim":
                generatePrim(maze);
                break;
            case "recursive":
                generateRecursiveDivision(maze, 1, rows-2, 1, cols-2);
                break;
            default:
                generateDFS(maze);
        }
        return maze;
    }
    // ===== DFS 迷宫生成 =====
    private static void generateDFS(int[][] maze) {
        int rows = maze.length, cols = maze[0].length;
        for (int[] row : maze) Arrays.fill(row, WALL);
        Stack<int[]> stack = new Stack<>();
        int startR = 1, startC = 1;
        maze[startR][startC] = PATH;
        stack.push(new int[]{startR, startC});
        int[][] dirs = {{-2,0},{2,0},{0,-2},{0,2}};
        Random rand = new Random();

        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            int r = cur[0], c = cur[1];
            List<int[]> neighbors = new ArrayList<>();
            for (int[] d : dirs) {
                int nr = r + d[0], nc = c + d[1];
                if (nr > 0 && nr < rows-1 && nc > 0 && nc < cols-1 && maze[nr][nc] == WALL) {
                    neighbors.add(new int[]{nr, nc});
                }
            }
            if (neighbors.isEmpty()) {
                stack.pop();
            } else {
                int[] next = neighbors.get(rand.nextInt(neighbors.size()));
                maze[(r + next[0]) / 2][(c + next[1]) / 2] = PATH;
                maze[next[0]][next[1]] = PATH;
                stack.push(next);
            }
        }
    }
    // ===== Prim 迷宫生成 =====
    private static void generatePrim(int[][] maze) {
        int rows = maze.length, cols = maze[0].length;
        for (int[] row : maze) Arrays.fill(row, WALL);
        Random rand = new Random();
        List<int[]> walls = new ArrayList<>();
        int startR = 1, startC = 1;
        maze[startR][startC] = PATH;
        addWalls(walls, startR, startC, maze);
        int[][] dirs = {{-2,0},{2,0},{0,-2},{0,2}};

        while (!walls.isEmpty()) {
            int[] wall = walls.remove(rand.nextInt(walls.size()));
            int wr = wall[0], wc = wall[1];
            int cellR = wall[2], cellC = wall[3];
            if (maze[cellR][cellC] == PATH) continue;
            maze[wr][wc] = PATH;
            maze[cellR][cellC] = PATH;
            addWalls(walls, cellR, cellC, maze);
        }
    }
    private static void addWalls(List<int[]> walls, int r, int c, int[][] maze) {
        int[][] dirs = {{-2,0},{2,0},{0,-2},{0,2}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr > 0 && nr < maze.length-1 && nc > 0 && nc < maze[0].length-1 && maze[nr][nc] == 1) {
                walls.add(new int[]{ (r+nr)/2, (c+nc)/2, nr, nc });
            }
        }
    }
    // ===== 递归分割迷宫生成 =====
    private static void generateRecursiveDivision(int[][] maze, int rowStart, int rowEnd, int colStart, int colEnd) {
        // 递归终止条件
        if (rowEnd - rowStart < 2 || colEnd - colStart < 2) {
            for (int r = rowStart; r <= rowEnd; r++) {
                for (int c = colStart; c <= colEnd; c++) {
                    maze[r][c] = PATH;
                }
            }
            return;
        }
        // 随机选择分割方向和位置
        boolean horizontal = new Random().nextBoolean();
        if (horizontal) {
            int divideRow = rowStart + 1 + new Random().nextInt((rowEnd - rowStart) / 2) * 2;
            for (int c = colStart; c <= colEnd; c++) maze[divideRow][c] = WALL;
            int passageCol = colStart + new Random().nextInt((colEnd - colStart) / 2 + 1) * 2;
            maze[divideRow][passageCol] = PATH;
            generateRecursiveDivision(maze, rowStart, divideRow-1, colStart, colEnd);
            generateRecursiveDivision(maze, divideRow+1, rowEnd, colStart, colEnd);
        } else {
            int divideCol = colStart + 1 + new Random().nextInt((colEnd - colStart) / 2) * 2;
            for (int r = rowStart; r <= rowEnd; r++) maze[r][divideCol] = WALL;
            int passageRow = rowStart + new Random().nextInt((rowEnd - rowStart) / 2 + 1) * 2;
            maze[passageRow][divideCol] = PATH;
            generateRecursiveDivision(maze, rowStart, rowEnd, colStart, divideCol-1);
            generateRecursiveDivision(maze, rowStart, rowEnd, divideCol+1, colEnd);
        }
    }
}