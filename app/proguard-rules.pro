# Retrofit, Room and Clerk supply consumer rules. Gson DTO field names are the wire contract.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep,allowoptimization class tools.interviews.android.data.api.APIInterview { *; }
-keep,allowoptimization class tools.interviews.android.data.api.APICompany { *; }
-keep,allowoptimization class tools.interviews.android.data.api.APIStage { *; }
-keep,allowoptimization class tools.interviews.android.data.api.APIStageMethod { *; }
-keep,allowoptimization class tools.interviews.android.data.api.APIMetadata { *; }
-keep,allowoptimization class tools.interviews.android.data.api.CreateInterviewRequest { *; }
-keep,allowoptimization class tools.interviews.android.data.api.UpdateInterviewRequest { *; }
-keep,allowoptimization class tools.interviews.android.data.api.ErrorResponse { *; }
