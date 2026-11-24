package com.slink.ansimheung.publiclayer;

import com.slink.ansimheung.common.type.PublicLayerKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicLayerRepository extends JpaRepository<PublicLayer, Long> {

    List<PublicLayer> findByKind(PublicLayerKind kind);
}
