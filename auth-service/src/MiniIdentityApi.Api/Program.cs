using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi;
using MiniIdentityApi.Api;
using MiniIdentityApi.Api.Middleware;
using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Application.Services;
using MiniIdentityApi.Domain.Entities;
using MiniIdentityApi.Infrastructure.Persistence;
using MiniIdentityApi.Infrastructure.Repositories;
using MiniIdentityApi.Infrastructure.Security;
using System.Text;
using System.Text.Json;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.RateLimiting;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();

builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "MiniIdentity API",
        Version = "v1"
    });

    options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        In = ParameterLocation.Header,
        Description = "Please enter a valid token.",
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        BearerFormat = "JWT",
        Scheme = "bearer"
    });

    options.AddSecurityRequirement(document => new()
    {
        [new OpenApiSecuritySchemeReference("Bearer", document)] = []
    });
});

// Persistencia: PostgreSQL, esquema propio dentro de la base "sisalert".
// En desarrollo la cadena viene de appsettings.Development.json; en producción de la
// variable de entorno ConnectionStrings__Identity. Sin ella el servicio no arranca.
var connectionString = builder.Configuration.GetConnectionString("Identity")
    ?? throw new InvalidOperationException(
        "Falta la cadena de conexión: defina la variable de entorno ConnectionStrings__Identity.");

// Esquema de la tabla de historial de migraciones: "public" por compatibilidad con las bases
// locales existentes; en una base nueva con usuarios por servicio (infra/db) se usa "identidad"
// con Persistencia__EsquemaHistorial=identidad, porque ese usuario no puede escribir en "public".
var esquemaHistorial = builder.Configuration["Persistencia:EsquemaHistorial"] ?? IdentityDbContext.EsquemaHistorial;

builder.Services.AddDbContext<IdentityDbContext>(options => options.UseNpgsql(connectionString,
        npgsql => npgsql
            .MigrationsHistoryTable("__EFMigrationsHistory", esquemaHistorial)
            // Reintenta ante cortes transitorios de red, frecuentes con bases en la nube.
            .EnableRetryOnFailure(maxRetryCount: 3)));

builder.Services.AddScoped<IUserRepository, EfUserRepository>();
builder.Services.AddScoped<IRoleRepository, EfRoleRepository>();
// PBKDF2; los hashes SHA-256 anteriores se aceptan y se actualizan en el siguiente login.
builder.Services.AddSingleton<IPasswordHasher, Pbkdf2PasswordHasher>();
builder.Services.AddSingleton<ITokenService, JwtTokenService>();

builder.Services.AddScoped<AuthenticationService>();
builder.Services.AddScoped<AuthorizationService>();
builder.Services.AddScoped<UserService>();
builder.Services.AddScoped<RoleService>();

// La clave de firma nunca va en appsettings.json: en producción llega por la variable de
// entorno Jwt__Key (desde Key Vault). HS256 exige al menos 256 bits (32 bytes).
var jwtKey = builder.Configuration["Jwt:Key"]
    ?? throw new InvalidOperationException("Falta la clave JWT: defina la variable de entorno Jwt__Key.");
if (Encoding.UTF8.GetByteCount(jwtKey) < 32)
{
    throw new InvalidOperationException("La clave JWT (Jwt__Key) debe tener al menos 32 caracteres.");
}

var jwtIssuer = builder.Configuration["Jwt:Issuer"]
    ?? throw new InvalidOperationException("Jwt:Issuer is missing in configuration.");

var jwtAudience = builder.Configuration["Jwt:Audience"]
    ?? throw new InvalidOperationException("Jwt:Audience is missing in configuration.");

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwtIssuer,
            ValidAudience = jwtAudience,
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(jwtKey))
        };
    });

builder.Services.AddAuthorization();

// Límite de intentos para login y registro, por dirección IP: frena la fuerza bruta
// de contraseñas y la creación masiva de cuentas. Configurable con RateLimit__IntentosPorMinuto.
var intentosPorMinuto = builder.Configuration.GetValue("RateLimit:IntentosPorMinuto", 10);
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.AddPolicy(PoliticasLimite.Autenticacion, context =>
        RateLimitPartition.GetFixedWindowLimiter(
            context.Connection.RemoteIpAddress?.ToString() ?? "desconocida",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = intentosPorMinuto,
                Window = TimeSpan.FromMinutes(1),
                QueueLimit = 0
            }));
    options.OnRejected = async (context, cancellationToken) =>
    {
        context.HttpContext.Response.ContentType = "application/json; charset=utf-8";
        await context.HttpContext.Response.WriteAsync(JsonSerializer.Serialize(new
        {
            error = "DEMASIADOS_INTENTOS",
            mensaje = "Demasiados intentos. Espere un minuto e intente de nuevo."
        }), cancellationToken);
    };
});

// CORS solo para los orígenes configurados (Cors__Origenes, separados por comas).
// Si no se configura ninguno, no se habilita CORS: el frontend llega por el mismo origen (proxy).
var origenesCors = (builder.Configuration["Cors:Origenes"] ?? "")
    .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
builder.Services.AddCors(options => options.AddDefaultPolicy(policy =>
    policy.WithOrigins(origenesCors)
        .WithMethods("GET", "POST", "PUT", "PATCH", "DELETE")
        .WithHeaders("Authorization", "Content-Type")));

var app = builder.Build();

app.UseMiddleware<ExceptionHandlingMiddleware>();

// Migraciones y datos iniciales. Se hace en un scope porque los repositorios
// ahora son scoped (dependen del DbContext).
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<IdentityDbContext>();
    db.Database.Migrate();

    var userRepository = scope.ServiceProvider.GetRequiredService<IUserRepository>();
    var roleRepository = scope.ServiceProvider.GetRequiredService<IRoleRepository>();
    var passwordHasher = scope.ServiceProvider.GetRequiredService<IPasswordHasher>();

    var adminRole = roleRepository.FindByName("Admin");
    if (adminRole is null)
    {
        adminRole = new Role("Admin");
        adminRole.AddPermission(new Permission("users.read", "Can read users"));
        adminRole.AddPermission(new Permission("users.manage", "Can manage users"));
        adminRole.AddPermission(new Permission("roles.read", "Can read roles"));
        adminRole.AddPermission(new Permission("roles.manage", "Can manage roles"));

        roleRepository.Save(adminRole);
    }

    var adminUser = userRepository.FindByUsernameOrEmail("admin");
    if (adminUser is null)
    {
        // La contraseña inicial del administrador no está en el código: en desarrollo viene de
        // appsettings.Development.json y en producción de la variable Seed__AdminPassword.
        // Solo se usa la primera vez, cuando el usuario admin todavía no existe.
        var adminPassword = builder.Configuration["Seed:AdminPassword"];
        if (string.IsNullOrWhiteSpace(adminPassword))
        {
            throw new InvalidOperationException(
                "No existe el usuario admin y falta su contraseña inicial: defina Seed__AdminPassword.");
        }

        var salt = passwordHasher.GenerateSalt();
        var hash = passwordHasher.Hash(adminPassword, salt);

        var credential = new Credential(hash, salt);
        adminUser = new User("admin", "admin@example.com", credential);
        adminUser.AddRole(adminRole);

        userRepository.Save(adminUser);
    }
}

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseCors();
app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();

app.Run();
