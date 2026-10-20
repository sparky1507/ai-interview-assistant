package com.harsh.interviewassistant.document.dto;

import com.harsh.interviewassistant.document.UserDocument;

/** A document plus its extracted text, so the user can check what the parser actually saw. */
public record DocumentDetailResponse(DocumentResponse document, String content) {

    public static DocumentDetailResponse from(UserDocument doc) {
        return new DocumentDetailResponse(DocumentResponse.from(doc), doc.getContent());
    }
}
