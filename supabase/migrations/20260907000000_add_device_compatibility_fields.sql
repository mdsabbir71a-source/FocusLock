-- Device compatibility telemetry used by the private FocusLock owner dashboard.
alter table public.devices
  add column if not exists manufacturer text,
  add column if not exists model text,
  add column if not exists android_version text,
  add column if not exists sdk_int integer,
  add column if not exists app_version_code integer,
  add column if not exists usage_access_granted boolean,
  add column if not exists overlay_granted boolean,
  add column if not exists battery_optimization_ignored boolean,
  add column if not exists notifications_granted boolean,
  add column if not exists app_enabled boolean,
  add column if not exists capabilities_updated_at timestamptz;

alter table public.devices
  add constraint devices_manufacturer_length check (manufacturer is null or char_length(manufacturer) <= 80),
  add constraint devices_model_length check (model is null or char_length(model) <= 120),
  add constraint devices_android_version_length check (android_version is null or char_length(android_version) <= 30),
  add constraint devices_sdk_int_range check (sdk_int is null or (sdk_int >= 21 and sdk_int <= 100)),
  add constraint devices_app_version_code_range check (app_version_code is null or app_version_code >= 1);