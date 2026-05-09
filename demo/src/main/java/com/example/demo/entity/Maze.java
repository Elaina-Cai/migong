package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("maze")
public class Maze {
    @TableId(type = IdType.AUTO)
    private Long id;          //迷宫Id
    private Long userId;
    private Integer rowsNum;
    private Integer colsNum;
    private String algorithm;
    private String gridData;       // JSON 字符串，存储二维数组
    private Integer playerRow;
    private Integer playerCol;
    private Integer startRow;
    private Integer startCol;
    private Integer endRow;
    private Integer endCol;
    private String itemPositions;  // JSON 数组，存储道具坐标列表
    private Integer status;        // 1=进行中, 2=通关, 3=失败
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isSaved;       //是否已保存
    private String mazeName;       //用户对该迷宫副本的自定义名，方便用户对其管理
    private LocalDateTime savedAt; //保存时间
    private LocalDateTime startTime; //游玩开始时间
    private Integer elapsedSeconds;  //通过关所花费的时间
}
