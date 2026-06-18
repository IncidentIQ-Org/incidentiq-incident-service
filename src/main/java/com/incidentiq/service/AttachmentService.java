package com.incidentiq.service;

import com.incidentiq.dto.AttachmentDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {
    AttachmentDto uploadAttachment(Long incidentId, MultipartFile file) throws Exception;
    
    List<AttachmentDto> getAttachmentsForIncident(Long incidentId);
    
    byte[] downloadAttachment(Long attachmentId);
    
    AttachmentDto getAttachmentDetails(Long attachmentId);
}
