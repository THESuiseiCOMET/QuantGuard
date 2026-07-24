package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreateSecurityInstrumentRequest;
import com.chenbo.quantguard.dto.SecurityInstrumentResponse;

import java.util.List;

public interface SecurityInstrumentService {

    SecurityInstrumentResponse create(CreateSecurityInstrumentRequest request);

    List<SecurityInstrumentResponse> listAll();

    SecurityInstrumentResponse getById(Long id);
}
