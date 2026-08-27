package com.sdd.platform.application.usecase.docparse;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class TestCoverageValidationService {

    public List<String> validateCoverage(
            List<String> specAcIds,
            List<String> matrixAcIds) {

        List<String> warnings = new ArrayList<>();

        for (String ac : specAcIds) {

            if (!matrixAcIds.contains(ac)) {
                warnings.add("AC_NOT_COVERED:" + ac);
            }
        }

        for (String ac : matrixAcIds) {

            if (!specAcIds.contains(ac)) {
                warnings.add("UNKNOWN_AC_REFERENCE:" + ac);
            }
        }

        return warnings;
    }
}