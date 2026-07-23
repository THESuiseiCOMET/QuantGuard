package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreateSimulationAccountRequest;
import com.chenbo.quantguard.dto.SimulationAccountResponse;

import java.util.List;

public interface SimulationAccountService {

    SimulationAccountResponse create(CreateSimulationAccountRequest request);

    List<SimulationAccountResponse> listAll();

    SimulationAccountResponse getById(Long id);
}
