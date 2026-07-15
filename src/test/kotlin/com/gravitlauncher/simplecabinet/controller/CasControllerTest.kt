package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.SimplecabinetApplication
import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.service.CasStorageService
import com.gravitlauncher.simplecabinet.service.PermissionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant

@SpringBootTest(classes = [SimplecabinetApplication::class])
@WebAppConfiguration
class CasControllerTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var casStorageService: CasStorageService

    @MockitoBean
    private lateinit var permissionService: PermissionService

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .build()
    }

    @Test
    fun `latest version endpoint requires authentication`() {
        whenever(permissionService.isAuthenticated()).thenReturn(false)

        mockMvc.perform(
            get("/cas/versions/latest")
                .param("directoryKey", "project/a")
                .param("branchName", "main")
        )
            .andExpect(status().isUnauthorized)

        verify(casStorageService, never()).getLatestVersion(any(), any())
    }

    @Test
    @WithMockUser(username = "reader", roles = ["USER"])
    fun `latest version endpoint returns data for authenticated user`() {
        whenever(permissionService.isAuthenticated()).thenReturn(true)
        whenever(casStorageService.getLatestVersion("project/a", "main")).thenReturn(
            DirectoryVersionResponse(
                id = 10L,
                directoryId = 1L,
                directoryKey = "project/a",
                branchName = "main",
                parentVersionId = 9L,
                manifestHash = "sha256:manifest",
                manifest = "{\"files\":[]}",
                metadata = "{\"tool\":\"publisher\"}",
                createdAt = Instant.parse("2026-07-07T00:00:00Z"),
                createdBy = 7L,
                message = "published"
            )
        )

        mockMvc.perform(
            get("/cas/versions/latest")
                .param("directoryKey", "project/a")
                .param("branchName", "main")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.directoryKey").value("project/a"))
            .andExpect(jsonPath("$.branchName").value("main"))

        verify(casStorageService).getLatestVersion("project/a", "main")
    }

    @Test
    @WithMockUser(username = "writer", roles = ["ADMIN"])
    fun `publish endpoint requires admin permission`() {
        whenever(permissionService.isAuthenticated()).thenReturn(true)
        whenever(permissionService.hasPermission("admin.cas.write")).thenReturn(false)

        mockMvc.perform(
            post("/cas/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "directoryKey": "project/a",
                      "branchName": "main",
                      "manifest": "{\"files\":[]}",
                      "metadata": "{}"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)

        verify(casStorageService, never()).publishVersion(any())
    }

    @Test
    @WithMockUser(username = "writer", roles = ["ADMIN"])
    fun `publish endpoint delegates to service when permission is granted`() {
        whenever(permissionService.isAuthenticated()).thenReturn(true)
        whenever(permissionService.hasPermission("admin.cas.write")).thenReturn(true)
        whenever(casStorageService.publishVersion(any())).thenReturn(
            DirectoryVersionResponse(
                id = 11L,
                directoryId = 1L,
                directoryKey = "project/a",
                branchName = "main",
                parentVersionId = null,
                manifestHash = "sha256:new",
                manifest = "{\"files\":[]}",
                metadata = "{}",
                createdAt = Instant.parse("2026-07-07T00:10:00Z"),
                createdBy = 42L,
                message = "Initial publish"
            )
        )

        mockMvc.perform(
            post("/cas/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "directoryKey": "project/a",
                      "branchName": "main",
                      "manifest": "{\"files\":[]}",
                      "metadata": "{}",
                      "createdBy": 42,
                      "message": "Initial publish"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(11))
            .andExpect(jsonPath("$.branchName").value("main"))

        verify(casStorageService).publishVersion(any())
    }

    @Test
    @WithMockUser(username = "writer", roles = ["ADMIN"])
    fun `upload endpoint delegates multipart file to service`() {
        whenever(permissionService.isAuthenticated()).thenReturn(true)
        whenever(permissionService.hasPermission("admin.cas.write")).thenReturn(true)
        whenever(casStorageService.upload(any())).thenReturn(blobResponse(20L, "sha256:single"))

        mockMvc.perform(
            multipart("/cas/upload")
                .file(MockMultipartFile("file", "single.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, "single".toByteArray()))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.hash").value("sha256:single"))

        verify(casStorageService).upload(any())
    }

    @Test
    @WithMockUser(username = "writer", roles = ["ADMIN"])
    fun `bulk upload endpoint delegates multipart files to service`() {
        whenever(permissionService.isAuthenticated()).thenReturn(true)
        whenever(permissionService.hasPermission("admin.cas.write")).thenReturn(true)
        whenever(casStorageService.bulkUpload(any())).thenReturn(
            listOf(blobResponse(21L, "sha256:first"), blobResponse(22L, "sha256:second"))
        )

        mockMvc.perform(
            multipart("/cas/upload/bulk")
                .file(MockMultipartFile("files", "first.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, "first".toByteArray()))
                .file(MockMultipartFile("files", "second.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, "second".toByteArray()))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].hash").value("sha256:first"))
            .andExpect(jsonPath("$[1].hash").value("sha256:second"))

        verify(casStorageService).bulkUpload(any())
    }

    private fun blobResponse(id: Long, hash: String) = BlobResponse(
        id = id,
        hash = hash,
        algorithm = "SHA-256",
        sizeBytes = 6,
        storageKey = "blobs/sha256/test/$id",
        createdAt = Instant.parse("2026-07-07T00:00:00Z")
    )
}
