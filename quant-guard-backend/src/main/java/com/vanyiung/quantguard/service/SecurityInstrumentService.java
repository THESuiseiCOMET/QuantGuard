package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateSecurityInstrumentRequest;
import com.vanyiung.quantguard.dto.SecurityInstrumentResponse;

import java.util.List;

public interface SecurityInstrumentService {

    SecurityInstrumentResponse create(CreateSecurityInstrumentRequest request);

    List<SecurityInstrumentResponse> listAll();

    SecurityInstrumentResponse getById(Long id);
}
