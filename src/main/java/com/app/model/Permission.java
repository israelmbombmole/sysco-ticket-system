package com.app.model;

public enum Permission {

    DASHBOARD,
    DATA_ENTRY,
    DATA_MANAGEMENT,
    DATASHARE,
    MY_ACTIVITY,
    MY_WORK,
    TICKET_MONITORING,
    TICKET_MANAGEMENT,
    FILE_SHARE_MANAGEMENT,
    USER_MANAGEMENT,
    LOGIN_AUDIT,
    FILE_SHARE_AUDIT,
    CREATE_TICKET,
    JOB_SCHEDULER,
    /** Leave (congé) management module. */
    LEAVE_MANAGEMENT,
    /** Internal physical courrier (mail) tracking. */
    PHYSICAL_COURIER,
    /** MyShift — attendance (sign-in with face + location). */
    MY_SHIFT
}