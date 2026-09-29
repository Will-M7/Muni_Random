INSERT INTO fiscalizador (id, codigo, nombre, zona, telefono, activo) VALUES
('FIS-001', 'FIS-001', 'Carlos Mendoza Mamani', 'Zona Centro - San Miguel', '984 512 301', TRUE),
('FIS-002', 'FIS-002', 'Patricia Vega Quispe', 'Zona Av. Triunfo / Circunvalación', '976 234 890', TRUE),
('FIS-003', 'FIS-003', 'Roberto Quispe Condori', 'Zona Huancané / Tacna', '955 890 124', TRUE);

INSERT INTO app_user (username, password_hash, display_name, cargo, role, fiscalizador_id, activo) VALUES
('municipio', '$2b$10$nkT/BPxmnZe2ClwQQmCKfu3qi13WS77tvdl2GUzZ3H88SnLw0Jyh6', 'Ventanilla Municipal · San Miguel', 'Atención al Ciudadano y Trámites Catastrales', 'MUNICIPIO', NULL, TRUE),
('municipio@munisanmiguel.gob.pe', '$2b$10$nkT/BPxmnZe2ClwQQmCKfu3qi13WS77tvdl2GUzZ3H88SnLw0Jyh6', 'Ventanilla Municipal · San Miguel', 'Atención al Ciudadano y Trámites Catastrales', 'MUNICIPIO', NULL, TRUE),
('fiscalizador', '$2b$10$EwZnxG1xqhjnlAFZNykLUe8cM4pObuWDXkUX/bZkaY9gWGq0rdW1u', 'Ing. Carlos Mendoza Mamani', 'Fiscalizador Técnico de Campo', 'FISCALIZADOR', 'FIS-001', TRUE),
('carlos.mendoza', '$2b$10$EwZnxG1xqhjnlAFZNykLUe8cM4pObuWDXkUX/bZkaY9gWGq0rdW1u', 'Ing. Carlos Mendoza Mamani', 'Fiscalizador Técnico de Campo', 'FISCALIZADOR', 'FIS-001', TRUE);

INSERT INTO solicitud (codigo,nombres,apellidos,dni,telefono,direccion,referencia,documento,documento_tamano,latitud,longitud,fecha,hora,fiscalizador_id,estado,observaciones,resultado_visita,fecha_registro) VALUES
('SM-2026-00418','Juan Carlos','Díaz Morales','45872136','987 654 321','Av. Triunfo 340, San Miguel','A media cuadra de la Plaza de Armas de San Miguel','DNI_Juan_Diaz.pdf','1.2 MB',-15.4781200,-70.1251500,'2026-09-24','09:00 a. m.','FIS-001','ASIGNADA','El solicitante estará en el domicilio a partir de las 8:45 a. m.',NULL,'2026-09-20'),
('SM-2026-00419','María Rosa','Ramos Quispe','70120455','942 189 240','Jr. Mariano Melgar 215, San Miguel','Frente a la I.E. San Miguel','Recibo_Luz_Melgar.pdf','840 KB',-15.4792500,-70.1238000,'2026-09-24','10:00 a. m.','FIS-001','EN FISCALIZACIÓN','Fiscalizador en ruta hacia el predio.',NULL,'2026-09-21'),
('SM-2026-00420','Carlos Alberto','Quispe Huamán','41982054','955 432 119','Jr. 28 de Julio 520, San Miguel','Esquina con Jr. San Román','Titulo_Propiedad_28Julio.pdf','2.4 MB',-15.4768900,-70.1264200,'2026-09-24','11:00 a. m.','FIS-001','AGENDADA','Documentación predial completa.',NULL,'2026-09-21'),
('SM-2026-00421','Ana Sofía','Castillo Vega','48761234','998 112 334','Av. Circunvalación 1050, San Miguel','Frente al grifo San Miguel','Constancia_Domicilio_Castillo.pdf','1.8 MB',-15.4812100,-70.1221000,'2026-09-24','02:30 p. m.','FIS-001','VERIFICADA','Visita técnica concluida conforme. Se constató vivencia efectiva del titular.','Predio habitado por el titular. Se levantó acta de verificación domiciliaria con firma conforme.','2026-09-18'),
('SM-2026-00422','Jorge Alberto','Morales Chávez','10456789','981 776 554','Jr. Huancané 410, San Miguel','A dos cuadras del mercado de San Miguel','Contrato_Alquiler_Morales.pdf','3.1 MB',-15.4754000,-70.1285000,'2026-09-25','09:00 a. m.','FIS-002','LISTA PARA AGENDAR','Falta validar DNI con RENIEC.',NULL,'2026-09-22'),
('SM-2026-00423','Elena Beatriz','Paredes Torres','72345678','974 651 223','Jr. San Román 325, San Miguel','Barrio Santa Bárbara','DNI_Elena_Paredes.pdf','950 KB',-15.4801500,-70.1271000,'2026-09-25','10:30 a. m.','FIS-002','PENDIENTE DE REVISIÓN','Ingresado por Mesa de Partes Digital.',NULL,'2026-09-22'),
('SM-2026-00424','Roberto Manuel','Mendoza Cárdenas','09871234','966 890 123','Jr. Tacna 180, San Miguel','Entre Jr. Mariano Melgar y Jr. Bolognesi','Recibo_Agua_Tacna.pdf','620 KB',-15.4776000,-70.1235000,'2026-09-24','04:00 p. m.','FIS-001','OBSERVADA','Predio deshabitado al momento de la inspección. Notificación dejada bajo puerta.','No se encontró a nadie en el domicilio tras 15 minutos de espera. Se dejó cédula de reprogramación.','2026-09-19');

INSERT INTO solicitud_historial (solicitud_id, fecha, estado, nota)
SELECT id, fecha_registro, 'REGISTRADA', 'Ingreso por ventanilla' FROM solicitud WHERE codigo='SM-2026-00418';
INSERT INTO solicitud_historial (solicitud_id, fecha, estado, nota)
SELECT id, '2026-09-21', 'ASIGNADA', 'Asignado a fiscalizador Carlos Mendoza' FROM solicitud WHERE codigo='SM-2026-00418';
INSERT INTO solicitud_historial (solicitud_id, fecha, estado, nota)
SELECT id, fecha_registro, 'REGISTRADA', NULL FROM solicitud WHERE codigo IN ('SM-2026-00419','SM-2026-00420');
INSERT INTO solicitud_historial (solicitud_id, fecha, estado, nota)
SELECT id, '2026-09-22', 'ASIGNADA', NULL FROM solicitud WHERE codigo='SM-2026-00419';
INSERT INTO solicitud_historial (solicitud_id, fecha, estado, nota)
SELECT id, '2026-09-24', 'EN FISCALIZACIÓN', 'En camino a la visita' FROM solicitud WHERE codigo='SM-2026-00419';
