package com.precious.syncres.shared.dto.cv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadFileResponseDto {
    private String filename;
    private ResponseInputStream<GetObjectResponse> fileStream;
}
