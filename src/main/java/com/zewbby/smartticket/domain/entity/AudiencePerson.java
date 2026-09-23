package com.zewbby.smartticket.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("audience_person")
public class AudiencePerson {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String idType;

    private String idNoCiphertext;

    private String idNoHash;

    private String status;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
