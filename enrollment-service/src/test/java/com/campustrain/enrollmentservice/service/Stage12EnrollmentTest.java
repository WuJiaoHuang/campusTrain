package com.campustrain.enrollmentservice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campustrain.enrollmentservice.client.TrainingClient;
import com.campustrain.enrollmentservice.client.UserClient;
import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.campustrain.enrollmentservice.entity.Enrollment;
import com.campustrain.enrollmentservice.exception.BusinessException;
import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingEnrollmentCheckVO;
import com.campustrain.enrollmentservice.remote.vo.RemoteUserVO;
import com.campustrain.enrollmentservice.service.impl.EnrollmentServiceImpl;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Stage12EnrollmentTest {
    private UserClient users;
    private TrainingClient trainings;
    private PlatformTransactionManager tx;
    private EnrollmentServiceImpl service;
    private RemoteUserVO user;
    private RemoteTrainingEnrollmentCheckVO training;
    private EnrollmentCreateDTO request;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "stage12"), Enrollment.class);
        users = mock(UserClient.class);
        trainings = mock(TrainingClient.class);
        tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        service = spy(new EnrollmentServiceImpl(users, trainings, tx));
        doReturn(false).when(service).exists(any());
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(Enrollment.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        user = new RemoteUserVO(); user.setId(1L); user.setRole("STUDENT"); user.setStatus(1);
        training = new RemoteTrainingEnrollmentCheckVO(); training.setTrainingId(2L); training.setTeacherId(3L);
        training.setStatus(1); training.setCapacity(10); training.setEnrolledCount(0);
        when(users.getUserById(anyLong())).thenAnswer(inv -> ok(user));
        when(trainings.getTrainingById(anyLong())).thenAnswer(inv -> ok(training));
        when(trainings.increment(anyLong())).thenReturn(ok(null));
        when(trainings.decrement(anyLong())).thenReturn(ok(null));
        when(trainings.restore(anyLong())).thenReturn(ok(null));
        request = new EnrollmentCreateDTO(); request.setTrainingId(2L);
        UserContext.set(1L, "STUDENT", "student", "trace");
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test void firstEnrollmentReservesThenSaves() {
        service.enroll(request);
        verify(trainings).increment(2L); verify(service).save(any(Enrollment.class));
        verify(tx).commit(any()); verify(trainings, never()).decrement(anyLong());
    }

    @Test void duplicateStopsBeforeRemoteCalls() {
        doReturn(true).when(service).exists(any());
        assertThrows(BusinessException.class, () -> service.enroll(request));
        verifyNoInteractions(users, trainings);
    }

    @Test void cancelledRecordIsReactivatedWithoutInsert() {
        Enrollment old = record(0); old.setId(42L);
        doReturn(old).when(service).getOne(any(Wrapper.class));
        assertEquals(42L, service.enroll(request));
        verify(service).update(any(Wrapper.class)); verify(service, never()).save(any(Enrollment.class));
    }

    @Test void rejectedUserAndTrainingConditionsNeverReserve() {
        user.setRole("TEACHER"); assertThrows(BusinessException.class, () -> service.enroll(request));
        user.setRole("STUDENT"); user.setStatus(0); assertThrows(BusinessException.class, () -> service.enroll(request));
        user.setStatus(1); training.setStatus(0); assertThrows(BusinessException.class, () -> service.enroll(request));
        training.setStatus(1); training.setEnrollStartTime(LocalDateTime.now().plusDays(1));
        assertThrows(BusinessException.class, () -> service.enroll(request));
        training.setEnrollStartTime(null); training.setEnrollEndTime(LocalDateTime.now().minusDays(1));
        assertThrows(BusinessException.class, () -> service.enroll(request));
        training.setEnrollEndTime(null); training.setEnrolledCount(10);
        assertThrows(BusinessException.class, () -> service.enroll(request));
        when(trainings.getTrainingById(2L)).thenReturn(ok(null));
        assertThrows(BusinessException.class, () -> service.enroll(request));
        verify(trainings, never()).increment(anyLong());
    }

    @Test void dependencyFailureReturns503WithoutWriting() {
        when(users.getUserById(1L)).thenReturn(RemoteResult.unavailable("unavailable"));
        assertEquals(503, assertThrows(BusinessException.class, () -> service.enroll(request)).getCode());
        when(users.getUserById(1L)).thenReturn(ok(user));
        when(trainings.getTrainingById(2L)).thenReturn(RemoteResult.unavailable("unavailable"));
        assertEquals(503, assertThrows(BusinessException.class, () -> service.enroll(request)).getCode());
        verify(service, never()).save(any(Enrollment.class)); verify(trainings, never()).increment(anyLong());
    }

    @Test void failedLocalCommitReleasesReservedPlace() {
        doThrow(new TransactionSystemException("commit failed")).when(tx).commit(any());
        assertThrows(TransactionSystemException.class, () -> service.enroll(request));
        verify(trainings).increment(2L); verify(trainings).decrement(2L);
    }

    @Test void countFallbackFailsWithoutLocalWrite() {
        when(trainings.increment(2L)).thenReturn(RemoteResult.unavailable("unavailable"));
        assertEquals(503, assertThrows(BusinessException.class, () -> service.enroll(request)).getCode());
        verify(service, never()).save(any(Enrollment.class));
        verify(trainings, never()).decrement(anyLong());
    }

    @Test void compensationFailureDoesNotHideOriginalFailure() {
        doThrow(new TransactionSystemException("commit failed")).when(tx).commit(any());
        when(trainings.decrement(2L)).thenReturn(RemoteResult.unavailable("unavailable"));
        TransactionSystemException failure = assertThrows(TransactionSystemException.class, () -> service.enroll(request));
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test void cancelAndDuplicateCancel() {
        Enrollment record = record(1); doReturn(record).when(service).getOne(any(Wrapper.class));
        service.cancel(7L); verify(trainings).decrement(2L);
        record.setStatus(0); assertThrows(BusinessException.class, () -> service.cancel(7L));
        verify(trainings, times(1)).decrement(2L);
    }

    @Test void failedCancellationCommitRestoresPlace() {
        doReturn(record(1)).when(service).getOne(any(Wrapper.class));
        doThrow(new TransactionSystemException("commit failed")).when(tx).commit(any());
        assertThrows(TransactionSystemException.class, () -> service.cancel(7L));
        verify(trainings).decrement(2L); verify(trainings).restore(2L);
    }

    @Test void rosterChecksRoleAndOwnership() {
        assertThrows(BusinessException.class, () -> service.trainingEnrollments(2L, 1, 10, null));
        UserContext.set(1L, "TEACHER", "teacher", "trace"); user.setRole("TEACHER");
        assertThrows(BusinessException.class, () -> service.trainingEnrollments(2L, 1, 10, null));
        training.setTeacherId(1L);
        doReturn(new Page<Enrollment>(1, 10, 0)).when(service).page(any(Page.class), any(Wrapper.class));
        assertEquals(0, service.trainingEnrollments(2L, 1, 10, null).getTotal());
        UserContext.set(1L, "ADMIN", "admin", "trace"); user.setRole("ADMIN"); training.setTeacherId(3L);
        assertEquals(0, service.trainingEnrollments(2L, 1, 10, null).getTotal());
        assertEquals(0, service.myEnrollments(1, 10, 1).getTotal());
    }

    private Enrollment record(int status) {
        Enrollment e = new Enrollment(); e.setId(7L); e.setUserId(1L); e.setTrainingId(2L); e.setStatus(status); return e;
    }

    private static <T> RemoteResult<T> ok(T data) {
        RemoteResult<T> result = new RemoteResult<>(); result.setCode(200); result.setData(data); return result;
    }
}
