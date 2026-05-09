package com.example.demo.dto;

import lombok.Data;

@Data
public class LoadMazeRequest {
    private Long mazeId;
    private boolean saveCurrent;
    private String currentMazeName;
}
