# syntax=docker/dockerfile:1
# API de identidad (ASP.NET Core, .NET 10)

# ---- Compilación ----
FROM mcr.microsoft.com/dotnet/sdk:10.0-alpine AS build
WORKDIR /src
# Primero los proyectos, para que la restauración de paquetes quede en caché.
COPY MiniIdentityApi.slnx ./
COPY src/MiniIdentityApi.Domain/MiniIdentityApi.Domain.csproj src/MiniIdentityApi.Domain/
COPY src/MiniIdentityApi.Application/MiniIdentityApi.Application.csproj src/MiniIdentityApi.Application/
COPY src/MiniIdentityApi.Infrastructure/MiniIdentityApi.Infrastructure.csproj src/MiniIdentityApi.Infrastructure/
COPY src/MiniIdentityApi.Api/MiniIdentityApi.Api.csproj src/MiniIdentityApi.Api/
RUN dotnet restore src/MiniIdentityApi.Api/MiniIdentityApi.Api.csproj
COPY src src
RUN dotnet publish src/MiniIdentityApi.Api/MiniIdentityApi.Api.csproj -c Release -o /out --no-restore

# ---- Ejecución ----
FROM mcr.microsoft.com/dotnet/aspnet:10.0-alpine
WORKDIR /app
COPY --from=build /out .
# Usuario sin privilegios que ya trae la imagen oficial de .NET.
USER $APP_UID

ENV ASPNETCORE_ENVIRONMENT=Production \
    ASPNETCORE_HTTP_PORTS=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["dotnet", "MiniIdentityApi.Api.dll"]
