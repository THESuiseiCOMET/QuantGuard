package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateSimulationAccountRequest;
import com.vanyiung.quantguard.dto.SimulationAccountResponse;
import com.vanyiung.quantguard.enums.AccountStatus;

import java.util.List;

public interface SimulationAccountService {

    SimulationAccountResponse create(CreateSimulationAccountRequest request);

    List<SimulationAccountResponse> listAll();

    SimulationAccountResponse getById(Long id);

    SimulationAccountResponse updateStatus(Long id, AccountStatus status);

    void delete(Long id);
}
