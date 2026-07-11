package com.stocksimulator.simulation;

import com.stocksimulator.simulation.dto.SimulationRequest;
import com.stocksimulator.simulation.dto.SimulationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Slf4j
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/simulate")
    public ResponseEntity<SimulationResult> simulate(@Valid @RequestBody SimulationRequest request) {
        log.info("Simulation request received for symbol: {}", request.getSymbol());
        try {
            SimulationResult result = simulationService.simulate(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Invalid simulation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
