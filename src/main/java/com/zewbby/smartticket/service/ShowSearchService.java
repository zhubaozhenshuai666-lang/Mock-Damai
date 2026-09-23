package com.zewbby.smartticket.service;

import com.zewbby.smartticket.domain.vo.ShowListVO;

import java.util.List;

public interface ShowSearchService {

    List<ShowListVO> search(String keyword, Integer limit, String clientIp);
}
