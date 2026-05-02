package com.example.demo.dto;

import lombok.Data;

@Data
public class MazeGenerateRequest {
    private Integer rows;
    private Integer cols;
    private String algorithm;  // dfs / prim / recursive
}