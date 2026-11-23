package ar.edu.unq.desapp.archfixture.service.sample;

import ar.edu.unq.desapp.archfixture.persistence.sql.interfaces.sample.SampleSQLDAO;

// Rompe B3.4 a propósito: el servicio usa un SQLDAO y se saltea el repository.
public class DirectDaoService {

    private final SampleSQLDAO sampleSQLDAO;

    public DirectDaoService(SampleSQLDAO sampleSQLDAO) {
        this.sampleSQLDAO = sampleSQLDAO;
    }

    public long countItems() {
        return sampleSQLDAO.count();
    }
}
