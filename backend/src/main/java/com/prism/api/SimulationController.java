package com.prism.api;

import com.prism.live.SettlementSimulator;
import com.prism.transaction.TransactionDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SettlementSimulator simulator;

    public SimulationController(SettlementSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDto simulate(@Valid @RequestBody SimulateRequest request) {
        return simulator.createLiveTransaction(request);
    }
}
