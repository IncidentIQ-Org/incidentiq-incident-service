package com.incidentiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDto {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Boolean isSafe;
    private String scanResult;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}
