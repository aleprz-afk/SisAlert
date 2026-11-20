using System.Text.Json;

namespace MiniIdentityApi.Api.Middleware;

/// <summary>
/// Traduce las excepciones de negocio a respuestas JSON consistentes
/// ({ error, mensaje }) en lugar de dejar que ASP.NET devuelva un 500 con stack trace.
/// </summary>
public class ExceptionHandlingMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<ExceptionHandlingMiddleware> _logger;

    public ExceptionHandlingMiddleware(RequestDelegate next, ILogger<ExceptionHandlingMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await _next(context);
        }
        catch (Exception ex)
        {
            var (status, code, mensaje) = ex switch
            {
                ArgumentException => (StatusCodes.Status400BadRequest, "SOLICITUD_INVALIDA", ex.Message),
                UnauthorizedAccessException => (StatusCodes.Status401Unauthorized, "NO_AUTORIZADO", ex.Message),
                KeyNotFoundException => (StatusCodes.Status404NotFound, "NO_ENCONTRADO", ex.Message),
                InvalidOperationException => (StatusCodes.Status409Conflict, "CONFLICTO", ex.Message),
                _ => (StatusCodes.Status500InternalServerError, "ERROR_INTERNO", "Ocurrió un error inesperado.")
            };

            if (status == StatusCodes.Status500InternalServerError)
                _logger.LogError(ex, "Error no controlado en la API de identidad");
            else
                _logger.LogWarning("Solicitud rechazada: {Mensaje}", ex.Message);

            context.Response.Clear();
            context.Response.StatusCode = status;
            context.Response.ContentType = "application/json; charset=utf-8";
            await context.Response.WriteAsync(JsonSerializer.Serialize(new { error = code, mensaje }));
        }
    }
}
