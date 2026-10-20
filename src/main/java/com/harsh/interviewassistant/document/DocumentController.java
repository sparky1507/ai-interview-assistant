package com.harsh.interviewassistant.document;

import com.harsh.interviewassistant.document.dto.DocumentDetailResponse;
import com.harsh.interviewassistant.document.dto.DocumentResponse;
import com.harsh.interviewassistant.document.dto.TextDocumentRequest;
import com.harsh.interviewassistant.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** All operations are scoped to the authenticated user; other users' documents behave as if they don't exist. */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a resume or job description (PDF, DOCX or TXT, max 5MB)",
            description = "Extracts the text, splits it into chunks and stores their embeddings for retrieval.")
    public DocumentResponse upload(@AuthenticationPrincipal UserPrincipal principal,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam("type") DocumentType type,
                                   @RequestParam(value = "title", required = false) String title) {
        return documentService.ingestFile(principal.getId(), type, file, title);
    }

    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a document from pasted text (e.g. a job description from a job board)")
    public DocumentResponse addText(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody TextDocumentRequest request) {
        return documentService.ingestText(principal.getId(), request);
    }

    @GetMapping
    @Operation(summary = "List my documents, newest first")
    public List<DocumentResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return documentService.list(principal.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a document including its extracted text")
    public DocumentDetailResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return documentService.get(principal.getId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document and its embeddings")
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        documentService.delete(principal.getId(), id);
    }
}
