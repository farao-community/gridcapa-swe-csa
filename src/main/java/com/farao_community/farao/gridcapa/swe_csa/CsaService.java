package com.farao_community.farao.gridcapa.swe_csa;

import com.rte_france.farao.rao_runner.api.resource.RaoRequest;
import com.rte_france.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class CsaService {

    private final AsynchronousRaoRunnerClient asynchronousRaoRunnerClient;

    public CsaService(AsynchronousRaoRunnerClient asynchronousRaoRunnerClient) {
        this.asynchronousRaoRunnerClient = asynchronousRaoRunnerClient;
    }

    public ResponseEntity runRao(MultipartFile inputFilesArchive) {
        // create a deployment with rao runner, minio and rabbitmq

        // import zip  to have a network and crac , as in unit tests
        // upload crac and network in minio (minio launched locally with docker compose)
        Network network = importNetwork(inputFilesArchive);
        String networkFileUrl = uploadIidmNetworkToMinio(destination, network, fileName, utcInstant);

        Crac crac = importCrac(inputFilesArchive);
        String cracFileUrl = uploadCracToMinio(destination, network, fileName, utcInstant);

        // create a rao request network and crac links
        RaoRequest raoRequest = new RaoRequest(UUID.randomUUID().toString(), networkFileUrl, cracFileUrl);
        asynchronousRaoRunnerClient.runRaoAsynchronously(raoRequest);
        // call RAO.run
        return ResponseEntity.accepted().build();
    }
}
