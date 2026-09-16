package it.abc.musical.controllers;

import it.abc.musical.dto.ShowDtos.ShowDetailDto;
import it.abc.musical.dto.ShowDtos.ShowSummaryDto;
import it.abc.musical.services.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/shows")
@RequiredArgsConstructor
public class PublicShowsController {

    private final ShowService showService;

    @GetMapping
    public List<ShowSummaryDto> list() {
        return showService.listPublic();
    }

    @GetMapping("/{id}")
    public ShowDetailDto detail(@PathVariable Long id) {
        return showService.getPublicDetail(id);
    }
}
