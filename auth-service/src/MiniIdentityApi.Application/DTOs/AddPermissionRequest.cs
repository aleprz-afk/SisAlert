namespace MiniIdentityApi.Application.DTOs.Roles;

public class AddPermissionRequest
{
    public string Code { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
}