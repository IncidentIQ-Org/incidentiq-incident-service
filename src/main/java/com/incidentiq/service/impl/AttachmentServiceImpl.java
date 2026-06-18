package com.incidentiq.service.impl;

import com.incidentiq.dto.AttachmentDto;
import com.incidentiq.model.Incident;
import com.incidentiq.model.IncidentAttachment;
import com.incidentiq.repository.IncidentAttachmentRepository;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.security.AuthorizationService;
import com.incidentiq.service.AttachmentService;
import com.incidentiq.util.ClamAVClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private final IncidentAttachmentRepository attachmentRepository;
    private final IncidentRepository incidentRepository;
    private final AuthorizationService authService;
    private final ClamAVClient clamAVClient;

    @Override
    @Transactional
    public AttachmentDto uploadAttachment(Long incidentId, MultipartFile file) throws Exception {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found"));

        Long currentUserId = authService.getCurrentUserId();

        byte[] data = file.getBytes();
        String scanResult;
        boolean isSafe = false;
        boolean scannerAvailable = true;

        try {
            scanResult = clamAVClient.scan(data);
            if (scanResult != null && scanResult.contains("OK")) {
                isSafe = true;
            }
        } catch (Exception e) {
            log.error("ClamAV scanner unreachable or scan error: {}", e.getMessage());
            scanResult = "SCAN_UNAVAILABLE: " + e.getMessage();
            scannerAvailable = false;
        }

        if (!scannerAvailable) {
            throw new com.incidentiq.exception.AttachmentRejectedException(
                    "File upload rejected: the antivirus scanner is temporarily unavailable. Please try again or contact your administrator.",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!isSafe) {
            throw new com.incidentiq.exception.AttachmentRejectedException(
                    "File rejected: a security threat was detected in the uploaded file (" + scanResult + ").",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }

        IncidentAttachment attachment = IncidentAttachment.builder()
                .incidentId(incidentId)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .data(data)
                .isSafe(isSafe)
                .scanResult(scanResult)
                .uploadedBy(currentUserId)
                .build();

        IncidentAttachment saved = attachmentRepository.save(attachment);

        return mapToDto(saved);
    }

    @Override
    public List<AttachmentDto> getAttachmentsForIncident(Long incidentId) {
        return attachmentRepository.findByIncidentId(incidentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public byte[] downloadAttachment(Long attachmentId) {
        IncidentAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        return attachment.getData();
    }

    @Override
    public AttachmentDto getAttachmentDetails(Long attachmentId) {
        IncidentAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        return mapToDto(attachment);
    }

    private AttachmentDto mapToDto(IncidentAttachment att) {
        return AttachmentDto.builder()
                .id(att.getId())
                .fileName(att.getFileName())
                .fileType(att.getFileType())
                .fileSize(att.getFileSize())
                .isSafe(att.getIsSafe())
                .scanResult(att.getScanResult())
                .uploadedBy(att.getUploadedBy())
                .uploadedAt(att.getUploadedAt())
                .build();
    }
}
