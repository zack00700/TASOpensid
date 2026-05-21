package fr.alb.askai.service;

import fr.alb.askai.dto.AnalysisResponse;
import fr.alb.askai.dto.UserQuery;

public interface Analyzer {
    AnalysisResponse analyze(UserQuery query, TableConfig cfg);
}
